package it.alantamanti.portshifttracker.data.local

import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceAutoTrigger
import it.alantamanti.portshifttracker.domain.BasePayEffect
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType

/**
 * Catalogo iniziale ricavato dalla tabella economica fornita dall'utente.
 * Le voci restano normali record Room: l'utente può modificarle o disattivarle.
 * insertIfMissing() fa sì che gli aggiornamenti dell'app non sovrascrivano
 * valori già personalizzati.
 */
object DefaultCatalog {

    fun rules(): List<AllowanceRuleEntity> = buildList {
        // TURNI / maggiorazioni del tipo di turno
        add(fixed("G", "Giornaliero", 0, AllowanceCategory.TURNO, 10, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("POM", "Pomeriggio", 0, AllowanceCategory.TURNO, 11, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("MAT", "Mattina", 0, AllowanceCategory.TURNO, 12, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("MATF", "Mattina festivo", 4793, AllowanceCategory.TURNO, 13, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("NOTTE", "Notturno feriale", 1136, AllowanceCategory.TURNO, 14, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("NOTTEF", "Notturno festivo", 5645, AllowanceCategory.TURNO, 15, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("POMS", "Pomeriggio sabato", 1136, AllowanceCategory.TURNO, 16, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("POMF", "Pomeriggio festivo", 4906, AllowanceCategory.TURNO, 17, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("SERA", "Serale feriale", 426, AllowanceCategory.TURNO, 18, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("SERA2", "Serale feriale 2", 1326, AllowanceCategory.TURNO, 19, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("SERAS", "Sera sabato", 1562, AllowanceCategory.TURNO, 20, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("SERAS2", "Sera sabato 2", 2462, AllowanceCategory.TURNO, 21, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("SERAF", "Serale festivo", 5219, AllowanceCategory.TURNO, 22, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("SERAF2", "Serale festivo 2", 6119, AllowanceCategory.TURNO, 23, group = "TIPO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))

        // AVVIAMENTO
        add(fixed("AVV_DISD_POSTO_FEST", "Disd.Posto.Fest", 2000, AllowanceCategory.AVVIAMENTO, 100))
        add(fixed("AVV_MEZZO_PRIMO", "MezzoPrimo", 0, AllowanceCategory.AVVIAMENTO, 101, enabled = false))
        add(hourly("AVV_FUORI_ORARIO_H", "FuoriOrario/h", 775, AllowanceCategory.AVVIAMENTO, 102))
        add(fixed("AVV_AFF_RE", "Aff.re", 500, AllowanceCategory.AVVIAMENTO, 103))
        add(fixed("AVV_DIS_CASA_FEST", "Dis.Casa.Fest", 1500, AllowanceCategory.AVVIAMENTO, 104))
        add(fixed("AVV_MEZZO_DOPPIO", "MezzoDoppio (legacy)", 0, AllowanceCategory.AVVIAMENTO, 105, enabled = false, performanceMask = PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("AVV_UN_ORA_PRIMA", "UnOraPrima", 1000, AllowanceCategory.AVVIAMENTO, 106))
        add(fixed("AVV_NO_SOST", "NoSost", 1500, AllowanceCategory.AVVIAMENTO, 107))
        add(fixed("AVV_DISD_POSTO", "Disd.Posto", 1250, AllowanceCategory.AVVIAMENTO, 108))
        add(fixed("AVV_DIS_CASA", "Dis.Casa", 1000, AllowanceCategory.AVVIAMENTO, 109))
        add(fixed("AVV_CHIAMATA_CASA", "ChiamataCasa", 516, AllowanceCategory.AVVIAMENTO, 110))
        add(fixed("AVV_FISIOS", "Fisios", 3105, AllowanceCategory.AVVIAMENTO, 111))
        add(fixed("AVV_IMA", "IMA", 5230, AllowanceCategory.AVVIAMENTO, 112))
        add(fixed("AVV_FT", "FT", 7900, AllowanceCategory.AVVIAMENTO, 113))
        add(fixed("AVV_MM", "MM", 6320, AllowanceCategory.AVVIAMENTO, 114))
        add(fixed("AVV_INAIL", "Inail", 6780, AllowanceCategory.AVVIAMENTO, 115))
        add(fixed("AVV_RE", "Re", 0, AllowanceCategory.AVVIAMENTO, 116, enabled = false))
        add(fixed("AVV_RP", "Rp", 0, AllowanceCategory.AVVIAMENTO, 117, enabled = false))
        add(fixed("AVV_DS", "Ds", 9500, AllowanceCategory.AVVIAMENTO, 118))
        add(fixed("AVV_MS", "Ms", 0, AllowanceCategory.AVVIAMENTO, 119, enabled = false))
        add(fixed("AVV_CONGEDO", "Congedo", 3000, AllowanceCategory.AVVIAMENTO, 120))

        // DISAGI
        add(fixed("DIS_BIG_BAGS", "BigBags", 258, AllowanceCategory.DISAGIO, 200))
        add(fixed("DIS_IMB_COILS", "Imb Coils", 510, AllowanceCategory.DISAGIO, 201))
        add(fixed("DIS_PUL_STIVA", "Pul.Stiva", 516, AllowanceCategory.DISAGIO, 202))
        add(fixed("DIS_AFF_RE", "Aff.re", 500, AllowanceCategory.DISAGIO, 203))
        add(fixed("DIS_TUBI", "Tubi", 775, AllowanceCategory.DISAGIO, 204))
        add(fixed("DIS_SBA_COILS", "Sba. Coils", 258, AllowanceCategory.DISAGIO, 205))
        add(fixed("DIS_LAMI_TOND", "Lami-Tond", 1000, AllowanceCategory.DISAGIO, 206))
        add(fixed("DIS_RIZZ_RORO", "Rizz-roro", 516, AllowanceCategory.DISAGIO, 207))
        add(fixed("DIS_RIZZ_CTS", "Rizz. Cts", 1000, AllowanceCategory.DISAGIO, 208))
        add(fixed("DIS_MERCE_VARIA", "MerceVaria", 775, AllowanceCategory.DISAGIO, 209))
        add(fixed("DIS_DO", "Do", 800, AllowanceCategory.DISAGIO, 210))
        add(fixed("DIS_RORO", "Roro", 885, AllowanceCategory.DISAGIO, 211))
        add(fixed("DIS_PIOGGIA", "Pioggia", 1000, AllowanceCategory.DISAGIO, 212))

        // AREA. Gruppo esclusivo: normalmente si sceglie un'area per turno.
        add(fixed("AREA_C2JLQKX", "C2JLQKX", 570, AllowanceCategory.AREA, 300, group = "AREA"))
        add(fixed("AREA_A5", "A5", 1330, AllowanceCategory.AREA, 301, group = "AREA"))
        add(fixed("AREA_BCDHO", "BCDHO", 110, AllowanceCategory.AREA, 302, group = "AREA"))
        add(fixed("AREA_S", "S", 260, AllowanceCategory.AREA, 303, group = "AREA"))
        add(fixed("AREA_S2", "S2", 420, AllowanceCategory.AREA, 304, group = "AREA"))
        add(fixed("AREA_AFF", "aff.", 0, AllowanceCategory.AREA, 305, group = "AREA"))
        add(fixed("AREA_J3", "J3", 570, AllowanceCategory.AREA, 306, group = "AREA"))
        add(fixed("AREA_Q2", "Q2", 932, AllowanceCategory.AREA, 307, group = "AREA"))
        add(fixed("AREA_W", "W", 878, AllowanceCategory.AREA, 308, group = "AREA"))
        add(fixed("AREA_J2", "J2", 950, AllowanceCategory.AREA, 309, group = "AREA"))
        add(fixed("AREA_R", "R", 1500, AllowanceCategory.AREA, 310, group = "AREA"))
        add(fixed("AREA_V", "V", 1150, AllowanceCategory.AREA, 311, group = "AREA"))
        add(fixed("AREA_G1", "G1", 830, AllowanceCategory.AREA, 312, group = "AREA"))
        add(fixed("AREA_G2", "G2", 1230, AllowanceCategory.AREA, 313, group = "AREA"))
        add(fixed("AREA_G3", "G3", 1930, AllowanceCategory.AREA, 314, group = "AREA"))
        add(fixed("AREA_U", "U", 570, AllowanceCategory.AREA, 315, group = "AREA"))

        // DOPPI
        add(fixed("DOP_POM", "Pom", 0, AllowanceCategory.DOPPIO, 400, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_SERAS", "SeraS", 1562, AllowanceCategory.DOPPIO, 401, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_POMS", "PomS", 1136, AllowanceCategory.DOPPIO, 402, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_POMF", "PomF", 4906, AllowanceCategory.DOPPIO, 403, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_SERA", "Sera", 426, AllowanceCategory.DOPPIO, 404, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_SERA2", "Sera2", 1326, AllowanceCategory.DOPPIO, 405, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_SERAS2", "SeraS2", 2462, AllowanceCategory.DOPPIO, 406, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_SERAF", "SeraF", 5219, AllowanceCategory.DOPPIO, 407, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_SERAF2", "SeraF2", 6119, AllowanceCategory.DOPPIO, 408, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_G", "G", 5300, AllowanceCategory.DOPPIO, 409, group = "DOPPIO", performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit))
        add(fixed("DOP_TU_MEZZO", "TUMezzo", 3390, AllowanceCategory.MEZZO_TURNO, 410, group = "MEZZO_TURNO", tagsCsv = "MEZZO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("DOP_ON_MEZZO", "ONmezzo", 4350, AllowanceCategory.MEZZO_TURNO, 411, group = "MEZZO_TURNO", tagsCsv = "MEZZO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))

        // ALTRE VOCI ECONOMICHE riportate nella parte inferiore della tabella.
        // Turno base è gestito nel profilo del lavoratore per non sommarlo due volte.
        add(fixed("ALT_BUON_PASTO", "BuonPasto", 500, AllowanceCategory.ALTRE_VOCI, 500))
        add(fixed("ALT_CRAL", "CRAL", 400, AllowanceCategory.ALTRE_VOCI, 501))
        add(fixed("ALT_FISIOS", "Fisios", 3105, AllowanceCategory.ALTRE_VOCI, 502))
        add(fixed("ALT_FERIE", "Ferie", 7900, AllowanceCategory.ALTRE_VOCI, 503, group = "SOSTITUISCE_BASE", basePayEffect = BasePayEffect.REPLACE_BASE, performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("ALT_MALATTIA", "Malattia", 6320, AllowanceCategory.ALTRE_VOCI, 504, group = "SOSTITUISCE_BASE", basePayEffect = BasePayEffect.REPLACE_BASE, performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("ALT_IMA", "IMA", 5230, AllowanceCategory.ALTRE_VOCI, 505, group = "SOSTITUISCE_BASE", basePayEffect = BasePayEffect.REPLACE_BASE, performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("ALT_MEZZA_IMA", "Mezza IMA", 2615, AllowanceCategory.ALTRE_VOCI, 5051, group = "SOSTITUISCE_BASE", turnAllowanceMultiplierBasisPoints = 5000, recommendedWithAnyTagCsv = "MEZZO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("ALT_POLIVALENZA", "Polivalenza", 852, AllowanceCategory.ALTRE_VOCI, 507, applicationMode = AllowanceApplicationMode.AUTO, autoTrigger = AllowanceAutoTrigger.WHEN_TURNO_SELECTED, performanceMask = PerformanceType.TURNO.maskBit))
        add(fixed("ALT_MOD_DOPPIO", "Mod.Doppio", 2000, AllowanceCategory.ALTRE_VOCI, 508))
        add(fixed("ALT_GIORNALIERO_87", "Giornaliero", 8700, AllowanceCategory.ALTRE_VOCI, 509))
        add(fixed("ALT_CONGEDI_LUI", "Congedi lui", 0, AllowanceCategory.ALTRE_VOCI, 510, enabled = false))
        add(fixed("ALT_CONGEDI_LEI", "Congedi lei", 0, AllowanceCategory.ALTRE_VOCI, 511, enabled = false))
    }

    private fun fixed(
        code: String,
        name: String,
        cents: Long,
        category: AllowanceCategory,
        priority: Int,
        group: String? = null,
        enabled: Boolean = true,
        basePayEffect: BasePayEffect = BasePayEffect.ADDITIVE,
        applicationMode: AllowanceApplicationMode = AllowanceApplicationMode.MANUAL,
        autoTrigger: AllowanceAutoTrigger = AllowanceAutoTrigger.NONE,
        turnAllowanceMultiplierBasisPoints: Int = 10000,
        tagsCsv: String? = null,
        recommendedWithAnyTagCsv: String? = null,
        performanceMask: Int = PerformanceType.entries.fold(0) { acc, type -> acc or type.maskBit }
    ) = AllowanceRuleEntity(
        name = name,
        code = code,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = cents,
        enabled = enabled,
        category = category,
        applicationMode = applicationMode,
        exclusiveGroup = group,
        priority = priority,
        basePayEffect = basePayEffect,
        autoTrigger = autoTrigger,
        turnAllowanceMultiplierBasisPoints = turnAllowanceMultiplierBasisPoints,
        tagsCsv = tagsCsv,
        recommendedWithAnyTagCsv = recommendedWithAnyTagCsv,
        performanceMask = performanceMask
    )

    private fun hourly(
        code: String,
        name: String,
        centsPerHour: Long,
        category: AllowanceCategory,
        priority: Int,
        performanceMask: Int = PerformanceType.entries.fold(0) { acc, type -> acc or type.maskBit }
    ) = AllowanceRuleEntity(
        name = name,
        code = code,
        calculationType = AllowanceCalculationType.PER_HOUR,
        value = centsPerHour,
        category = category,
        applicationMode = AllowanceApplicationMode.MANUAL,
        priority = priority,
        performanceMask = performanceMask
    )
}
