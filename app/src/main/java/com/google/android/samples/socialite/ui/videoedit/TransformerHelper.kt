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

package com.google.android.samples.socialite.ui.videoedit

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.google.android.samples.socialite.ui.camera.CameraViewModel
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class TransformerHelper {

    @OptIn(UnstableApi::class)
    fun transformVideo(
        context: Context, originalVideoUri: String,
        transformerListener: Transformer.Listener,
    ) {
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(transformerListener)
            .build()

        var transformedVideoFilePath = ""

        val editedMediaItem =
            EditedMediaItem.Builder(MediaItem.fromUri(originalVideoUri))
                .setEffects(
                    Effects(
                        listOf(),
                        listOf(
                            RgbFilter.createGrayscaleFilter(),
                        ),
                    ),
                )
                .build()

        val editedVideoFileName = "Socialite-edited-recording-" +
            SimpleDateFormat(CameraViewModel.FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".mp4"

        transformedVideoFilePath = createNewVideoFilePath(context, editedVideoFileName)

        // TODO: Investigate using MediaStoreOutputOptions instead of external cache file for saving
        //  edited video https://github.com/androidx/media/issues/504
        transformer.start(editedMediaItem, transformedVideoFilePath)
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
}
