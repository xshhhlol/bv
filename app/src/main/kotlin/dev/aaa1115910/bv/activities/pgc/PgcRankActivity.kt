package dev.aaa1115910.bv.activities.pgc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.bv.screen.main.pgc.PgcRankScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class PgcRankActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            pgcType: PgcType
        ) {
            context.startActivity(
                Intent(context, PgcRankActivity::class.java).apply {
                    putExtra("pgcType", pgcType.ordinal)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pgcType = PgcType.entries
            .getOrElse(intent.getIntExtra("pgcType", PgcType.Movie.ordinal)) { PgcType.Movie }
        setContent {
            BVTheme {
                PgcRankScreen(pgcType = pgcType)
            }
        }
    }
}
