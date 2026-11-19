package com.example.getyourmoneyupnotyourfunnyup.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.navigation.AppShell
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*

/**
 * Debug-only way to see the logged-in app (the same [AppShell] MainActivity shows after login)
 * without Firebase / google-services.json. Runs the real repositories on a throw-away in-memory
 * database seeded with sample people and groups.
 */
class Person2TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = ViewModelProvider(this, debugFactory { GroupsSandbox(applicationContext) })[GroupsSandbox::class.java]
        setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                Surface(color = AppColors.White, contentColor = AppColors.Ink900) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Surface(color = AppColors.Amber100, contentColor = AppColors.Amber800) {
                            Text(
                                "DEBUG · sample data on this phone only, no login or upload",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                style = AppTypography.Label
                            )
                        }
                        // The same shell MainActivity shows after login, on an in-memory database.
                        // Without a profile repository the Profile tab is read-only and logout does nothing.
                        AppShell(
                            user = session.me,
                            onLogout = {},
                            database = session.database,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private class GroupsSandbox(context: android.content.Context) : ViewModel() {
    val me = User("you", "victor@mail.dk", "Victor")
    val database = AppDatabase.createInMemory(context)

    init { seed() } // before any ViewModel starts observing

    private fun seed() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        listOf(
            UserEntity("you", "victor@mail.dk", "Victor"),
            UserEntity("mads", "mads.h@mail.dk", "Mads"),
            UserEntity("ida", "ida.lund@mail.dk", "Ida"),
            UserEntity("jonas", "jonas@mail.dk", "Jonas", phone = "+4522814017"),
            UserEntity("sofie", "sofie@kollegiet.dk", "Sofie"),
            UserEntity("emil", "emil@mail.dk", "Emil")
        ).forEach(database.userDao::upsertUser)
        fun group(id: String, name: String, colour: Int, fav: Boolean, ago: Long, members: List<String>) =
            database.groupDao.upsertGroupWithMembers(
                GroupEntity(id, name, currency = "DKK", createdBy = members.first(), createdAt = now - ago * day,
                    updatedAt = now - ago * day, colourIndex = colour, isFavourite = fav),
                members.mapIndexed { i, user ->
                    GroupMemberEntity(id, user, if (i == 0) GroupRole.ADMIN else GroupRole.MEMBER, now - ago * day + i)
                }
            )
        group("kol", "Kollegiet 4. sal", 0, true, 0, listOf("you", "ida", "jonas"))
        group("fri", "Friday Bar crew", 2, true, 0, listOf("sofie", "you", "mads"))
        group("ski", "Ski trip Åre", 1, true, 3, listOf("mads", "you", "ida", "jonas", "sofie", "emil"))
        group("fam", "Family dinners", 4, false, 6, listOf("you", "emil"))
    }

    override fun onCleared() = database.close()
}

private fun debugFactory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
