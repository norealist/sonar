package com.sonar.app.player

object PlayerControllerHolder {
    @Volatile
    var controller: AppPlayerController? = null
}
