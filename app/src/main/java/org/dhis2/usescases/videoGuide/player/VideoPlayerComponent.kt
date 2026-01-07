package org.dhis2.usescases.videoGuide.player

import dagger.Subcomponent
import org.dhis2.commons.di.dagger.PerActivity

@PerActivity
@Subcomponent(modules = [VideoPlayerModule::class])
interface VideoPlayerComponent {
    fun inject(activity: VideoPlayerActivity)
}

