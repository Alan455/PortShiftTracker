package it.alantamanti.portshifttracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.alantamanti.portshifttracker.domain.AllowanceLine
import it.alantamanti.portshifttracker.domain.PayBreakdown
import org.json.JSONArray
import org.json.JSONObject

/** Amounts actually recorded for a performance, independent of future rate edits. */
@Entity(
    tableName = "shift_pay_snapshots",
    foreignKeys = [ForeignKey(
        entity = ShiftEntity::class,
        parentColumns = ["id"],
        childColumns = ["shiftId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("shiftId")]
)
data class ShiftPaySnapshotEntity(
    @PrimaryKey val shiftId: Long,
    val totalMinutes: Long,
    val basePayCents: Long,
    val allowanceLinesJson: String,
    val savedAtEpochMillis: Long
) {
    fun toBreakdown(): PayBreakdown {
        val lines = JSONArray(allowanceLinesJson)
        return PayBreakdown(totalMinutes, basePayCents, buildList {
            for (index in 0 until lines.length()) {
                val line = lines.getJSONObject(index)
                add(AllowanceLine(
                    ruleId = line.getLong("ruleId"),
                    name = line.getString("name"),
                    eligibleMinutes = line.getLong("eligibleMinutes"),
                    amountCents = line.getLong("amountCents")
                ))
            }
        })
    }

    companion object {
        fun fromBreakdown(shiftId: Long, pay: PayBreakdown, savedAt: Long = System.currentTimeMillis()) =
            ShiftPaySnapshotEntity(
                shiftId = shiftId,
                totalMinutes = pay.totalMinutes,
                basePayCents = pay.basePayCents,
                allowanceLinesJson = JSONArray().apply {
                    pay.allowanceLines.forEach { line ->
                        put(JSONObject()
                            .put("ruleId", line.ruleId)
                            .put("name", line.name)
                            .put("eligibleMinutes", line.eligibleMinutes)
                            .put("amountCents", line.amountCents))
                    }
                }.toString(),
                savedAtEpochMillis = savedAt
            )
    }
}
