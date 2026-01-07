package org.dhis2.usescases.main

import dagger.Subcomponent
import org.dhis2.commons.di.dagger.PerActivity
import org.dhis2.usescases.troubleshooting.TroubleshootingComponent
import org.dhis2.usescases.troubleshooting.TroubleshootingModule
import org.dhis2.usescases.videoGuide.list.VideoGuideComponent
import org.dhis2.usescases.videoGuide.list.VideoGuideModule

@PerActivity
@Subcomponent(modules = [MainModule::class])
interface MainComponent {
    fun inject(mainActivity: MainActivity)
    fun plus(troubleShootingModule: TroubleshootingModule): TroubleshootingComponent
    fun plus(videoGuideModule: VideoGuideModule): VideoGuideComponent
}
