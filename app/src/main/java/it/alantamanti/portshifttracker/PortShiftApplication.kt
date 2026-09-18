package it.alantamanti.portshifttracker

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import it.alantamanti.portshifttracker.data.local.AppDatabase
import it.alantamanti.portshifttracker.data.local.DefaultCatalog
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.domain.BasePayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PortShiftApplication : Application() {
    lateinit var repository: PortRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "port_shift.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
        repository = PortRepository(db)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            seedDefaults(db)
        }
    }

    private suspend fun seedDefaults(db: AppDatabase) {
        if (db.workerDao().observeAll().first().isEmpty()) {
            db.workerDao().upsert(
                WorkerEntity(
                    name = "Lavoratore",
                    hourlyRateCents = 0,
                    basePayMode = BasePayMode.FIXED_PER_SHIFT,
                    baseShiftCents = 6780,
                    doubleBaseCents = 8840,
                    irpefBasisPoints = 3000,
                    senioritySteps = 3
                )
            )
        }

        // Aggiunge soltanto le voci mancanti. Le modifiche dell'utente restano intatte.
        DefaultCatalog.rules().forEach { db.allowanceRuleDao().insertIfMissing(it) }

        // Riallinea il catalogo voci sui database già esistenti.
        // Donazione sangue, Inail e Congedo non sono Avviamenti.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET category = 'ALTRE_VOCI', priority = 512, " +
                "performanceMask = 1, name = 'Donazione sangue' WHERE code = 'AVV_DS'"
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET category = 'ALTRE_VOCI', priority = 513, " +
                "performanceMask = 1 WHERE code = 'AVV_INAIL'"
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET category = 'ALTRE_VOCI', priority = 514, " +
                "performanceMask = 1 WHERE code = 'AVV_CONGEDO'"
        )

        // FT/MM/IMA/Fisios erano duplicati legacy delle corrispondenti voci in
        // "Altre voci". Prima di eliminarli migriamo eventuali selezioni storiche.
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT OR IGNORE INTO shift_allowance_selections (shiftId, ruleId)
            SELECT sas.shiftId, target.id
            FROM shift_allowance_selections sas
            JOIN allowance_rules legacy ON legacy.id = sas.ruleId
            JOIN allowance_rules target ON target.code = CASE legacy.code
                WHEN 'AVV_FT' THEN 'ALT_FERIE'
                WHEN 'AVV_MM' THEN 'ALT_MALATTIA'
                WHEN 'AVV_IMA' THEN 'ALT_IMA'
                WHEN 'AVV_FISIOS' THEN 'ALT_FISIOS'
            END
            WHERE legacy.code IN ('AVV_FT','AVV_MM','AVV_IMA','AVV_FISIOS')
            """.trimIndent()
        )
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM shift_allowance_selections WHERE ruleId IN (" +
                "SELECT id FROM allowance_rules WHERE code IN " +
                "('AVV_FT','AVV_MM','AVV_IMA','AVV_FISIOS'))"
        )
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM allowance_rules WHERE code IN ('AVV_FT','AVV_MM','AVV_IMA','AVV_FISIOS')"
        )

        // TUMezzo/ONmezzo sostituiscono il turno intero: manteniamo l'importo
        // eventualmente personalizzato, ma correggiamo il comportamento anche sui DB esistenti.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET basePayEffect = 'REPLACE_BASE', " +
                "turnAllowanceMultiplierBasisPoints = 5000 " +
                "WHERE code IN ('DOP_TU_MEZZO','DOP_ON_MEZZO')"
        )

        // Giornaliero: base fissa €90 sul primo turno. Con ONMezzo il motore usa il 50%.
        // Nel Doppio la voce DOP_G rappresenta invece il solo Mezzo Giornaliero da €45.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET name = 'Giornaliero', value = 9000, " +
                "category = 'TURNO', exclusiveGroup = 'TIPO_TURNO', " +
                "basePayEffect = 'REPLACE_BASE', performanceMask = 1, enabled = 1 " +
                "WHERE code = 'G'"
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET name = 'Mezzo Giornaliero', value = 4500, " +
                "category = 'DOPPIO', exclusiveGroup = 'DOPPIO', " +
                "basePayEffect = 'REPLACE_BASE', performanceMask = 2, enabled = 1 " +
                "WHERE code = 'DOP_G'"
        )

        // Converte la vecchia voce Giornaliero da €87 nel nuovo Giornaliero strutturale da €90.
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT OR IGNORE INTO shift_allowance_selections (shiftId, ruleId)
            SELECT sas.shiftId, target.id
            FROM shift_allowance_selections sas
            JOIN allowance_rules legacy ON legacy.id = sas.ruleId
            JOIN allowance_rules target ON target.code = 'G'
            WHERE legacy.code = 'ALT_GIORNALIERO_87'
            """.trimIndent()
        )
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM shift_allowance_selections WHERE ruleId IN (" +
                "SELECT id FROM allowance_rules WHERE code = 'ALT_GIORNALIERO_87')"
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET enabled = 0, name = 'Giornaliero (legacy)' " +
                "WHERE code = 'ALT_GIORNALIERO_87'"
        )

        // Normalizza gradualmente i record legacy. In presenza di vecchi duplicati,
        // il record conflittuale resta null per preservare lo storico.
        db.shiftDao().getWithoutServiceDay().forEach { shift ->
            val date = java.time.Instant.ofEpochMilli(shift.startEpochMillis)
                .atZone(java.time.ZoneId.of(shift.zoneId))
                .toLocalDate()
            runCatching {
                db.shiftDao().update(shift.copy(serviceEpochDay = date.toEpochDay()))
            }
        }
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workers ADD COLUMN basePayMode TEXT NOT NULL DEFAULT 'HOURLY'")
                db.execSQL("ALTER TABLE workers ADD COLUMN baseShiftCents INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workers ADD COLUMN irpefBasisPoints INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workers ADD COLUMN senioritySteps INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN category TEXT NOT NULL DEFAULT 'ALTRO'")
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN applicationMode TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN exclusiveGroup TEXT")

                // Disattiva i due preset dimostrativi della v1 per evitare doppie maggiorazioni.
                db.execSQL("UPDATE allowance_rules SET enabled = 0 WHERE code IN ('NIGHT', 'SUNDAY')")
                // Converte soltanto il profilo demo originale della v1 ai valori della tabella fornita.
                db.execSQL("UPDATE workers SET basePayMode = 'FIXED_PER_SHIFT', baseShiftCents = 6780, irpefBasisPoints = 3000, senioritySteps = 3 WHERE name = 'Lavoratore' AND hourlyRateCents = 1200")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shift_allowance_selections (
                        shiftId INTEGER NOT NULL,
                        ruleId INTEGER NOT NULL,
                        PRIMARY KEY(shiftId, ruleId),
                        FOREIGN KEY(shiftId) REFERENCES shifts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(ruleId) REFERENCES allowance_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_allowance_selections_shiftId ON shift_allowance_selections(shiftId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_allowance_selections_ruleId ON shift_allowance_selections(ruleId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN basePayEffect TEXT NOT NULL DEFAULT 'ADDITIVE'")
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN autoTrigger TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN turnAllowanceMultiplierBasisPoints INTEGER NOT NULL DEFAULT 10000")

                // Mantiene gli importi eventualmente personalizzati, aggiornando solo il comportamento.
                db.execSQL("UPDATE allowance_rules SET basePayEffect = 'REPLACE_BASE', exclusiveGroup = 'SOSTITUISCE_BASE' WHERE code IN ('ALT_FERIE','ALT_MALATTIA','ALT_IMA')")
                db.execSQL("UPDATE allowance_rules SET applicationMode = 'AUTO', autoTrigger = 'WHEN_TURNO_SELECTED' WHERE code = 'ALT_POLIVALENZA'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN tagsCsv TEXT")
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN recommendedWithAnyTagCsv TEXT")

                // Relazione morbida: Mezza IMA è normalmente un complemento di un mezzo turno,
                // ma non viene bloccata perché l'utente ha specificato che ciò avviene "di solito".
                db.execSQL("UPDATE allowance_rules SET tagsCsv = 'MEZZO_TURNO' WHERE code IN ('DOP_TU_MEZZO','DOP_ON_MEZZO')")
                db.execSQL("UPDATE allowance_rules SET recommendedWithAnyTagCsv = 'MEZZO_TURNO' WHERE code = 'ALT_MEZZA_IMA'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workers ADD COLUMN doubleBaseCents INTEGER NOT NULL DEFAULT 8840")
                db.execSQL("ALTER TABLE shifts ADD COLUMN performanceType TEXT NOT NULL DEFAULT 'TURNO'")
                db.execSQL("ALTER TABLE allowance_rules ADD COLUMN performanceMask INTEGER NOT NULL DEFAULT 3")

                // Se la vecchia voce Doppio era stata personalizzata, conserva quel valore
                // come nuova base del Doppio per il profilo lavoratore.
                db.execSQL("UPDATE workers SET doubleBaseCents = COALESCE((SELECT value FROM allowance_rules WHERE code = 'ALT_DOPPIO' LIMIT 1), 8840)")

                // Turno e Doppio diventano prestazioni distinte. La stessa giornata può
                // contenere più record (es. TURNO + DOPPIO), ognuno con le proprie voci.
                // Recupera i vecchi record chiaramente registrati come Doppio prima di
                // trasformare la vecchia voce ALT_DOPPIO in base strutturale.
                db.execSQL(
                    "UPDATE shifts SET performanceType = 'DOPPIO' WHERE id IN (" +
                        "SELECT sas.shiftId FROM shift_allowance_selections sas " +
                        "JOIN allowance_rules ar ON ar.id = sas.ruleId " +
                        "WHERE ar.code = 'ALT_DOPPIO' OR (ar.category = 'DOPPIO' AND ar.code NOT IN ('DOP_TU_MEZZO','DOP_ON_MEZZO'))" +
                    ")"
                )
                db.execSQL("UPDATE allowance_rules SET performanceMask = 1 WHERE category = 'TURNO'")
                db.execSQL("UPDATE allowance_rules SET performanceMask = 2 WHERE category = 'DOPPIO'")

                // TUMezzo e ONmezzo sono mezzi turni ordinari, non doppi.
                db.execSQL("UPDATE allowance_rules SET category = 'MEZZO_TURNO', exclusiveGroup = 'MEZZO_TURNO', performanceMask = 1 WHERE code IN ('DOP_TU_MEZZO','DOP_ON_MEZZO')")

                // MezzoDoppio è invece una voce specifica della prestazione DOPPIO.
                db.execSQL("UPDATE allowance_rules SET performanceMask = 2 WHERE code = 'AVV_MEZZO_DOPPIO'")

                // Ferie/Malattia/IMA/Mezza IMA e Polivalenza appartengono al turno ordinario.
                db.execSQL("UPDATE allowance_rules SET performanceMask = 1 WHERE code IN ('ALT_FERIE','ALT_MALATTIA','ALT_IMA','ALT_MEZZA_IMA','ALT_POLIVALENZA')")

                // Il Doppio da 88,40 ora è una base della prestazione, non una voce da sommare.
                // Elimina soltanto la vecchia selezione ALT_DOPPIO dai turni già convertiti:
                // l'importo è ora fornito da workers.doubleBaseCents.
                db.execSQL("DELETE FROM shift_allowance_selections WHERE ruleId IN (SELECT id FROM allowance_rules WHERE code = 'ALT_DOPPIO')")
                db.execSQL("UPDATE allowance_rules SET enabled = 0, performanceMask = 2 WHERE code = 'ALT_DOPPIO'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Qualunque voce già valida sul Doppio diventa valida anche sul Mezzo Doppio.
                // Il bit 4 identifica MEZZO_DOPPIO.
                db.execSQL("UPDATE allowance_rules SET performanceMask = performanceMask | 4 WHERE (performanceMask & 2) != 0")

                // I vecchi record v0.5 che avevano selezionato la voce MezzoDoppio vengono
                // convertiti nel nuovo tipo di prestazione strutturale MEZZO_DOPPIO.
                db.execSQL(
                    "UPDATE shifts SET performanceType = 'MEZZO_DOPPIO' WHERE performanceType = 'DOPPIO' AND id IN (" +
                        "SELECT sas.shiftId FROM shift_allowance_selections sas " +
                        "JOIN allowance_rules ar ON ar.id = sas.ruleId " +
                        "WHERE ar.code = 'AVV_MEZZO_DOPPIO'" +
                    ")"
                )
                db.execSQL("DELETE FROM shift_allowance_selections WHERE ruleId IN (SELECT id FROM allowance_rules WHERE code = 'AVV_MEZZO_DOPPIO')")

                // MezzoDoppio non è più una indennità da sommare: è il tipo di prestazione.
                db.execSQL("UPDATE allowance_rules SET enabled = 0, performanceMask = 4 WHERE code = 'AVV_MEZZO_DOPPIO'")

                // Polivalenza e Mezza IMA restano rigorosamente solo sul Turno ordinario.
                db.execSQL("UPDATE allowance_rules SET performanceMask = 1 WHERE code IN ('ALT_POLIVALENZA','ALT_MEZZA_IMA')")
            }
        }


        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN serviceEpochDay INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_shifts_workerId_serviceEpochDay_performanceType " +
                        "ON shifts(workerId, serviceEpochDay, performanceType)"
                )
            }
        }

    }
}
