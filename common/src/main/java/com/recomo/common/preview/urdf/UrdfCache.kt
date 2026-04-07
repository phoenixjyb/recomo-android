package com.recomo.common.preview.urdf

import android.content.Context

object UrdfCache {
    private val cache = mutableMapOf<String, UrdfModel>()

    fun get(context: Context, assetPath: String): UrdfModel {
        return cache.getOrPut(assetPath) {
            UrdfParser.loadFromAssets(context, assetPath)
        }
    }
}
