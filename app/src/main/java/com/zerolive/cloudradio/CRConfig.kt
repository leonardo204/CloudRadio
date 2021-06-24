package com.zerolive.cloudradio

enum class RELEASEMODE {
    OFFICIAL, PRIVATE
}

enum class ReleaseType(val value: RELEASEMODE) {
    TYPE(RELEASEMODE.OFFICIAL)
}