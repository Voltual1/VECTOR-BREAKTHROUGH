package me.voltual.vb

import me.voltual.vb.core.database.*
import me.voltual.vb.core.database.dao.*
import me.voltual.vb.data.UserAgreementDataStore
import me.voltual.vb.ui.settings.update.*
import me.voltual.vb.ui.scripts.ScriptViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { UpdateSettingsViewModel() }
    viewModel { ScriptViewModel(get()) } // 注入 ScriptDao
    
    single { UserAgreementDataStore(androidContext()) }
    single { BBQApplication.instance.database }
    single { get<AppDatabase>().logDao() }
    single { get<AppDatabase>().scriptDao() }
}