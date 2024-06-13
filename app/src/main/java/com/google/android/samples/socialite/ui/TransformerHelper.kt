/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.samples.socialite.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.android.samples.socialite.ui.camera.CameraViewModel
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(UnstableApi::class)
fun transformVideo(
    context: Context,
    originalVideoUri: String,
    onTransformationComplete: () -> Unit,
) {

    val transformer = Transformer.Builder(context)
        .addListener(
            object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    super.onCompleted(composition, exportResult)
                    onTransformationComplete()
                }
            },
        )
        .build()

    val mediaItem = MediaItem.fromUri(originalVideoUri)

    val outroImage = EditedMediaItem.Builder(
        MediaItem.fromUri("https://io.google/2024/app/images/io24-homepage-hero-bg.webp"),
    )
        .setDurationUs(2_000_000)
        .setFrameRate(30)
        .build()

    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
        .setEffects(
            Effects(
                listOf(), // audio processors
                listOf( //video effects
                    RgbFilter.createGrayscaleFilter(),
                    GaussianBlurWithFrameOverlaid(
                        /* sigma= */ 5f, /* scaleSharpX= */ 0.9f, /* scaleSharpY= */ .9f),
                ),
            ),
        )
        .build()

    transformer.start(
        Composition.Builder(
            EditedMediaItemSequence(
                editedMediaItem,
                outroImage,

                ),
        ).build(),
        createNewVideoFilePath(context),
    )
}

@UnstableApi
fun buildTextOverlayEffect(text: String): OverlayEffect {
    val overlaysBuilder = ImmutableList.Builder<TextureOverlay>()

    val spannableStringBuilder = SpannableStringBuilder(text)

    // Make text bigger
    spannableStringBuilder.setSpan(
        RelativeSizeSpan(2f), // Triple text size
        0,
        text.length,
        Spannable.SPAN_INCLUSIVE_INCLUSIVE,
    )

    // Make text white
    spannableStringBuilder.setSpan(
        ForegroundColorSpan(Color.WHITE),
        0,
        text.length,
        Spannable.SPAN_INCLUSIVE_INCLUSIVE,
    )

    // Make text bold
    spannableStringBuilder.setSpan(
        StyleSpan(Typeface.BOLD),
        0,
        text.length,
        Spannable.SPAN_INCLUSIVE_INCLUSIVE,
    )

    val textOverlay = TextOverlay.createStaticTextOverlay(
        SpannableString.valueOf(spannableStringBuilder),
    )

    overlaysBuilder.add(textOverlay)

    return OverlayEffect(overlaysBuilder.build())
}

private fun createNewVideoFilePath(context: Context): String {
    transformedVideoFilePath = createNewVideoFilePath(
        context,
        "Socialite-edited-recording-" +
            SimpleDateFormat(CameraViewModel.FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".mp4",
    )

    return transformedVideoFilePath
}

private fun createNewVideoFilePath(context: Context, fileName: String): String {
    val externalCacheFile = createExternalCacheFile(context, fileName)
    return externalCacheFile.absolutePath
}

/** Creates a cache file, resetting it if it already exists.  */
@Throws(IOException::class)
private fun createExternalCacheFile(context: Context, fileName: String): File {
    val file = File(context.externalCacheDir, fileName)
    check(!(file.exists() && !file.delete())) {
        "Could not delete the previous transformer output file"
    }
    check(file.createNewFile()) { "Could not create the transformer output file" }
    return file
}

