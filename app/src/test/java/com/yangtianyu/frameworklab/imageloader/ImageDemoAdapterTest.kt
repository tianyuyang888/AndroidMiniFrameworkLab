package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDemoAdapterTest {

    @Test
    fun declaresRecycleHookAndViewHolderCleanup() {
        val adapterClass = Class.forName(
            ImageDemoAdapter::class.java.name,
            false,
            javaClass.classLoader,
        )
        val viewHolderClass = Class.forName(
            ImageDemoAdapter.ImageDemoViewHolder::class.java.name,
            false,
            javaClass.classLoader,
        )

        assertTrue(
            adapterClass.declaredMethods.any { method ->
                method.name == "onViewRecycled" && method.parameterCount == 1
            },
        )
        assertTrue(
            viewHolderClass.declaredMethods.any { method ->
                method.name == "recycle" && method.parameterCount == 0
            },
        )
    }
}
