package dev.aaa1115910.bv.activities.pgc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.pgc.index.Area
import dev.aaa1115910.biliapi.entity.pgc.index.IndexOrder
import dev.aaa1115910.biliapi.entity.pgc.index.SeasonStatus
import dev.aaa1115910.biliapi.entity.pgc.index.Style
import dev.aaa1115910.bv.screen.main.pgc.PgcIndexScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class PgcIndexActivity : ComponentActivity() {
    companion object {
        /**
         * [indexOrder]、[seasonStatus]、[area]、[style] 为预设的筛选条件，不传时使用默认条件
         */
        fun actionStart(
            context: Context,
            pgcType: PgcType,
            indexOrder: IndexOrder? = null,
            seasonStatus: SeasonStatus? = null,
            area: Area? = null,
            style: Style? = null
        ) {
            context.startActivity(
                Intent(context, PgcIndexActivity::class.java).apply {
                    putExtra("pgcType", pgcType.ordinal)
                    indexOrder?.let { putExtra("indexOrder", it.name) }
                    seasonStatus?.let { putExtra("seasonStatus", it.name) }
                    area?.let { putExtra("area", it.name) }
                    style?.let { putExtra("style", it.name) }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                PgcIndexScreen()
            }
        }
    }
}
