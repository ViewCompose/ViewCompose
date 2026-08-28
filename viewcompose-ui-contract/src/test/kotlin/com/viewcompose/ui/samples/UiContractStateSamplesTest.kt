package com.viewcompose.ui.samples

import org.junit.Test

class UiContractStateSamplesTest {
    @Test
    fun documentedStateAndInteractionSamplesExecuteSuccessfully() {
        lazyListItemSessionUpdateSample()
        val session = object : com.viewcompose.ui.node.LazyListItemSession {
            override fun render(): Boolean = true
            override fun dispose() = Unit
        }
        val crossKeyStrategy = crossKeyLazyItemSessionStrategySample(
            createSession = { session },
            installItem = { _, _ -> },
        )
        check(crossKeyStrategy.canReuseAcrossKeys(session))
        platformFontFamilyIdentitySample()
        modifierChainSample()
        nestedScrollDispatcherSample()
        focusRequesterSample()
        lazyListStateSample()
        pagerStateSample()
        vNodeModelSample()
        animatedBoundsHostNodeContractSample()
        uiImageLoaderSample()
        uiImageRequestSample()
    }
}
