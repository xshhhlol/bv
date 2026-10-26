package dev.aaa1115910.bv.activities.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonType
import dev.aaa1115910.bv.screen.user.FollowingSeasonScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class FollowingSeasonActivity : ComponentActivity() {
    companion object {
        /**
         * @param type 进入时默认显示的追剧类型，不传时显示番剧
         */
        fun actionStart(
            context: Context,
            type: FollowingSeasonType? = null
        ) {
            context.startActivity(
                Intent(context, FollowingSeasonActivity::class.java).apply {
                    type?.let { putExtra("type", it.name) }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialType = intent.getStringExtra("type")
            ?.let { name -> FollowingSeasonType.entries.find { it.name == name } }
        setContent {
            BVTheme {
                FollowingSeasonScreen(initialType = initialType)
            }
        }
    }
}
