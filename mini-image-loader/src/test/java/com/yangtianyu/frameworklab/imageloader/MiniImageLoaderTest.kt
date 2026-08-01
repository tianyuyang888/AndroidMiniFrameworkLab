package com.yangtianyu.frameworklab.imageloader

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniImageLoaderTest {

    @Test
    fun exposesModuleName() {
        assertEquals("Mini Image Loader", MiniImageLoader.NAME)
    }

    @Test
    fun exposesTwoAndFourArgumentLoadMethods() {
        val loaderClass = Class.forName(
            MiniImageLoader::class.java.name,
            false,
            javaClass.classLoader,
        )
        val staticLoadParameterCounts = loaderClass.declaredMethods
            .filter { method ->
                method.name == "load" && Modifier.isStatic(method.modifiers)
            }
            .map { method -> method.parameterCount }

        assertTrue(staticLoadParameterCounts.contains(2))
        assertTrue(staticLoadParameterCounts.contains(4))
    }

    @Test
    fun ownsOneBitmapMemoryCache() {
        val loaderClass = Class.forName(
            MiniImageLoader::class.java.name,
            false,
            javaClass.classLoader,
        )
        val memoryCacheFieldCount = loaderClass.declaredFields.count { field ->
            field.type == BitmapMemoryCache::class.java
        }

        assertEquals(1, memoryCacheFieldCount)
    }
}
