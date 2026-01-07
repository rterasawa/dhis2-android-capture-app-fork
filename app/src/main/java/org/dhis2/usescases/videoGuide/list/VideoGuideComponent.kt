package org.dhis2.usescases.videoGuide.list

import dagger.Subcomponent
import org.dhis2.commons.di.dagger.PerFragment

@PerFragment
@Subcomponent(modules = [VideoGuideModule::class])
interface VideoGuideComponent {
    fun inject(fragment: VideoGuideFragment)
}

