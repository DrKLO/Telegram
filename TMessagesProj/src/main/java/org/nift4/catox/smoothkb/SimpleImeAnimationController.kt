/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package org.nift4.catox.smoothkb

import android.os.CancellationSignal
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.dynamicanimation.animation.springAnimationOf
import androidx.dynamicanimation.animation.withSpringForceProperties
import kotlin.math.roundToInt

/**
 * A wrapper around the [WindowInsetsAnimationControllerCompat] APIs in AndroidX Core, to simplify
 * the implementation of common use-cases around the IME.
 *
 * See [InsetsAnimationLinearLayout] and [InsetsAnimationTouchListener] for examples of how
 * to use this class.
 */
internal class SimpleImeAnimationController {
    private var insetsAnimationController: WindowInsetsAnimationControllerCompat? = null
    private var pendingRequestCancellationSignal: CancellationSignal? = null
    private var pendingRequestOnReady: ((WindowInsetsAnimationControllerCompat) -> Unit)? = null

    /* To take control of the an WindowInsetsAnimation, we need to pass in a listener to
       controlWindowInsetsAnimation() in startControlRequest(). The listener created here
       keeps track of the current WindowInsetsAnimationController and resets our state. */
    private val animationControlListener by lazy {
        object : WindowInsetsAnimationControlListenerCompat {
            /**
             * Once the request is ready, call our [onRequestReady] function
             */
            override fun onReady(
                controller: WindowInsetsAnimationControllerCompat,
                types: Int
            ) = onRequestReady(controller)

            /**
             * If the request is finished, we should reset our internal state
             */
            override fun onFinished(controller: WindowInsetsAnimationControllerCompat) = reset()

            /**
             * If the request is cancelled, we should reset our internal state
             */
            override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) = reset()
        }
    }

    /**
     * True if the IME was shown at the start of the current animation.
     */
    private var isImeShownAtStart = false

    private var currentSpringAnimation: SpringAnimation? = null

    /**
     * Start a control request to the [view]s [android.view.WindowInsetsController]. This should
     * be called once the view is in a position to take control over the position of the IME.
     *
     * @param view The view which is triggering this request
     * @param onRequestReady optional listener which will be called when the request is ready and
     * the animation can proceed
     */
    fun startControlRequest(
        view: View,
        onRequestReady: ((WindowInsetsAnimationControllerCompat) -> Unit)? = null
    ) {
        check(!isInsetAnimationInProgress()) {
            "Animation in progress. Can not start a new request to controlWindowInsetsAnimation()"
        }

        // Keep track of the IME insets, and the IME visibility, at the start of the request
        isImeShownAtStart = ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

        // Create a cancellation signal, which we pass to controlWindowInsetsAnimation() below
        pendingRequestCancellationSignal = CancellationSignal()
        // Keep reference to the onReady callback
        pendingRequestOnReady = onRequestReady

        // Finally we make a controlWindowInsetsAnimation() request:
        ViewCompat.getWindowInsetsController(view)?.controlWindowInsetsAnimation(
            // We're only catering for IME animations in this listener
            WindowInsetsCompat.Type.ime(),
            // Animation duration. This is not used by the system, and is only passed to any
            // WindowInsetsAnimation.Callback set on views. We pass in -1 to indicate that we're
            // not starting a finite animation, and that this is completely controlled by
            // the user's touch.
            -1,
            // The time interpolator used in calculating the animation progress. The fraction value
            // we passed into setInsetsAndAlpha() which be passed into this interpolator before
            // being used by the system to inset the IME. LinearInterpolator is a good type
            // to use for scrolling gestures.
            linearInterpolator,
            // A cancellation signal, which allows us to cancel the request to control
            pendingRequestCancellationSignal,
            // The WindowInsetsAnimationControlListener
            animationControlListener
        )
    }

    /**
     * Start a control request to the [view]s [android.view.WindowInsetsController], similar to
     * [startControlRequest], but immediately fling to a finish using [velocityY] once ready.
     *
     * This function is useful for fire-and-forget operations to animate the IME.
     *
     * @param view The view which is triggering this request
     * @param velocityY the velocity of the touch gesture which caused this call
     */
    fun startAndFling(view: View, velocityY: Float) = startControlRequest(view) {
        animateToFinish(velocityY)
    }

    /**
     * Update the inset position of the IME by the given [dy] value. This value will be coerced
     * into the hidden and shown inset values.
     *
     * This function should only be called if [isInsetAnimationInProgress] returns true.
     *
     * @return the amount of [dy] consumed by the inset animation, in pixels
     */
    fun insetBy(dy: Int): Int {
        val controller = insetsAnimationController
            ?: throw IllegalStateException(
                "Current WindowInsetsAnimationController is null." +
                        "This should only be called if isAnimationInProgress() returns true"
            )

        // Call updateInsetTo() with the new inset value
        return insetTo(controller.currentInsets.bottom - dy)
    }

    /**
     * Update the inset position of the IME to be the given [inset] value. This value will be
     * coerced into the hidden and shown inset values.
     *
     * This function should only be called if [isInsetAnimationInProgress] returns true.
     *
     * @return the distance moved by the inset animation, in pixels
     */
    fun insetTo(inset: Int): Int {
        val controller = insetsAnimationController
            ?: throw IllegalStateException(
                "Current WindowInsetsAnimationController is null." +
                        "This should only be called if isAnimationInProgress() returns true"
            )

        val hiddenBottom = controller.hiddenStateInsets.bottom
        val shownBottom = controller.shownStateInsets.bottom
        val startBottom = if (isImeShownAtStart) shownBottom else hiddenBottom
        val endBottom = if (isImeShownAtStart) hiddenBottom else shownBottom

        // We coerce the given inset within the limits of the hidden and shown insets
        val coercedBottom = inset.coerceIn(hiddenBottom, shownBottom)

        val consumedDy = controller.currentInsets.bottom - coercedBottom

        // Finally update the insets in the WindowInsetsAnimationController using
        // setInsetsAndAlpha().
        controller.setInsetsAndAlpha(
            // Here we update the animating insets. This is what controls where the IME is displayed.
            // It is also passed through to views via their WindowInsetsAnimation.Callback.
            Insets.of(0, 0, 0, coercedBottom),
            // This controls the alpha value. We don't want to alter the alpha so use 1f
            1f,
            // Finally we calculate the animation progress fraction. This value is passed through
            // to any WindowInsetsAnimation.Callbacks, but it is not used by the system.
            (coercedBottom - startBottom) / (endBottom - startBottom).toFloat()
        )

        return consumedDy
    }

    /**
     * Return `true` if an inset animation is in progress.
     */
    fun isInsetAnimationInProgress(): Boolean {
        return insetsAnimationController != null
    }

    /**
     * Return `true` if an inset animation is currently finishing.
     */
    fun isInsetAnimationFinishing(): Boolean {
        return currentSpringAnimation != null
    }

    /**
     * Return `true` if a request to control an inset animation is in progress.
     */
    fun isInsetAnimationRequestPending(): Boolean {
        return pendingRequestCancellationSignal != null
    }

    /**
     * Cancel the current [WindowInsetsAnimationControllerCompat]. We immediately finish
     * the animation, reverting back to the state at the start of the gesture.
     */
    fun cancel() {
        insetsAnimationController?.finish(isImeShownAtStart)
        pendingRequestCancellationSignal?.cancel()

        // Cancel the current spring animation
        currentSpringAnimation?.cancel()

        reset()
    }

    /**
     * Finish the current [WindowInsetsAnimationControllerCompat] immediately.
     */
    fun finish() {
        val controller = insetsAnimationController

        if (controller == null) {
            // If we don't currently have a controller, cancel any pending request and return
            pendingRequestCancellationSignal?.cancel()
            return
        }

        val current = controller.currentInsets.bottom
        val shown = controller.shownStateInsets.bottom
        val hidden = controller.hiddenStateInsets.bottom

        when (current) {
            // The current inset matches either the shown/hidden inset, finish() immediately
            shown -> controller.finish(true)
            hidden -> controller.finish(false)
            else -> {
                // Otherwise, we'll look at the current position...
                if (controller.currentFraction >= SCROLL_THRESHOLD) {
                    // If the IME is past the 'threshold' we snap to the toggled state
                    controller.finish(!isImeShownAtStart)
                } else {
                    // ...otherwise, we snap back to the original visibility
                    controller.finish(isImeShownAtStart)
                }
            }
        }
    }

    /**
     * Finish the current [WindowInsetsAnimationControllerCompat]. We finish the animation,
     * animating to the end state if necessary.
     *
     * @param velocityY the velocity of the touch gesture which caused this call to [animateToFinish].
     * Can be `null` if velocity is not available.
     */
    fun animateToFinish(velocityY: Float? = null) {
        val controller = insetsAnimationController

        if (controller == null) {
            // If we don't currently have a controller, cancel any pending request and return
            pendingRequestCancellationSignal?.cancel()
            return
        }

        val current = controller.currentInsets.bottom
        val shown = controller.shownStateInsets.bottom
        val hidden = controller.hiddenStateInsets.bottom

        when {
            // If we have a velocity, we can use it's direction to determine
            // the visibility. Upwards == visible
            velocityY != null -> animateImeToVisibility(
                visible = velocityY > 0,
                velocityY = velocityY
            )
            // The current inset matches either the shown/hidden inset, finish() immediately
            current == shown -> controller.finish(true)
            current == hidden -> controller.finish(false)
            else -> {
                // Otherwise, we'll look at the current position...
                if (controller.currentFraction >= SCROLL_THRESHOLD) {
                    // If the IME is past the 'threshold' we animate it to the toggled state
                    animateImeToVisibility(!isImeShownAtStart)
                } else {
                    // ...otherwise, we animate it back to the original visibility
                    animateImeToVisibility(isImeShownAtStart)
                }
            }
        }
    }

    private fun onRequestReady(controller: WindowInsetsAnimationControllerCompat) {
        // The request is ready, so clear out the pending cancellation signal
        pendingRequestCancellationSignal = null
        // Store the current WindowInsetsAnimationController
        insetsAnimationController = controller

        // Call any pending callback
        pendingRequestOnReady?.invoke(controller)
        pendingRequestOnReady = null
    }

    /**
     * Resets all of our internal state.
     */
    private fun reset() {
        // Clear all of our internal state
        insetsAnimationController = null
        pendingRequestCancellationSignal = null

        isImeShownAtStart = false

        currentSpringAnimation?.cancel()
        currentSpringAnimation = null

        pendingRequestOnReady = null
    }

    /**
     * Animate the IME to a given visibility.
     *
     * @param visible `true` to animate the IME to it's fully shown state, `false` to it's
     * fully hidden state.
     * @param velocityY the velocity of the touch gesture which caused this call. Can be `null`
     * if velocity is not available.
     */
    private fun animateImeToVisibility(
        visible: Boolean,
        velocityY: Float? = null
    ) {
        val controller = insetsAnimationController
            ?: throw IllegalStateException("Controller should not be null")

        currentSpringAnimation = springAnimationOf(
            setter = { insetTo(it.roundToInt()) },
            getter = { controller.currentInsets.bottom.toFloat() },
            finalPosition = when {
                visible -> controller.shownStateInsets.bottom.toFloat()
                else -> controller.hiddenStateInsets.bottom.toFloat()
            }
        ).withSpringForceProperties {
            // Tweak the damping value, to remove any bounciness.
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            // The stiffness value controls the strength of the spring animation, which
            // controls the speed. Medium (the default) is a good value, but feel free to
            // play around with this value.
            stiffness = SpringForce.STIFFNESS_MEDIUM
        }.apply {
            if (velocityY != null) {
                setStartVelocity(velocityY)
            }
            addEndListener { anim, _, _, _ ->
                if (anim == currentSpringAnimation) {
                    currentSpringAnimation = null
                }
                // Once the animation has ended, finish the controller
                finish()
            }
        }.also { it.start() }
    }
}

/**
 * Scroll threshold for determining whether to animating to the end state, or to the start state.
 * Currently 15% of the total swipe distance distance
 */
private const val SCROLL_THRESHOLD = 0.15f

/**
 * A LinearInterpolator instance we can re-use across listeners.
 */
private val linearInterpolator = LinearInterpolator()
