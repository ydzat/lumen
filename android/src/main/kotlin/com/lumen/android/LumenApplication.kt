package com.lumen.android

import android.app.Application
import com.lumen.companion.persona.PersonaManager
import com.lumen.core.di.companionModule
import com.lumen.core.di.memoryModule
import com.lumen.core.di.platformModule
import com.lumen.core.di.researchModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class LumenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LumenApplication)
            modules(platformModule, companionModule, memoryModule, researchModule)
        }
        GlobalContext.get().get<PersonaManager>().seedBuiltInPersonas()
    }
}
