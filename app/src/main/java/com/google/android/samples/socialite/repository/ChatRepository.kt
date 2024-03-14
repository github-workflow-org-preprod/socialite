/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.samples.socialite.repository

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Candidate
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.SafetyRating
import com.google.ai.client.generativeai.type.content
import com.google.android.samples.socialite.BuildConfig
import com.google.android.samples.socialite.data.ChatDao
import com.google.android.samples.socialite.data.ContactDao
import com.google.android.samples.socialite.data.MessageDao
import com.google.android.samples.socialite.di.AppCoroutineScope
import com.google.android.samples.socialite.model.ChatDetail
import com.google.android.samples.socialite.model.Message
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Singleton
class ChatRepository @Inject internal constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val notificationHelper: NotificationHelper,
    @AppCoroutineScope
    private val coroutineScope: CoroutineScope,
) {
    private var currentChat: Long = 0L

    val apiKey = "" // Insert API Key here

    init {
        notificationHelper.setUpNotificationChannels()
    }

    fun getChats(): Flow<List<ChatDetail>> {
        return chatDao.allDetails()
    }

    fun findChat(chatId: Long): Flow<ChatDetail?> {
        return chatDao.detailById(chatId)
    }

    fun findMessages(chatId: Long): Flow<List<Message>> {
        return messageDao.allByChatId(chatId)
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        mediaUri: String?,
        mediaMimeType: String?,
    ) {
        val detail = chatDao.loadDetailById(chatId) ?: return

        messageDao.insert(
            Message(
                id = 0L,
                chatId = chatId,
                // User
                senderId = 0L,
                text = text,
                mediaUri = mediaUri,
                mediaMimeType = mediaMimeType,
                timestamp = System.currentTimeMillis(),
            ),
        )
        notificationHelper.pushShortcut(detail.firstContact, PushReason.OutgoingMessage)

        // Simulate a response from the peer.
        // The code here is just for demonstration purpose in this sample.
        // Real apps will use their server backend and Firebase Cloud Messaging to deliver messages.
        coroutineScope.launch {
            // The person is typing...
//            delay(5000L)
            // Receive a reply.
//            messageDao.insert(
//                detail.firstContact.reply(text).apply { this.chatId = chatId }.build(),
//            )

            val generativeModel = GenerativeModel(
                modelName = "gemini-pro",
                apiKey = apiKey
            )

//            val pastMessages: MutableList<Content> = mutableListOf()
//            findMessages(chatId).collect { messageList ->
//                messageList.onEach { message ->
//                    if (message.text.isNotEmpty()) {
//                        val role = if (message.isIncoming) "user" else "model"
//                        pastMessages.add(content(role = role) { text(message.text) })
//                    }
//                }
//            }


            val pastMessages = findMessages(chatId).first().filter() { message ->
                message.text.isNotEmpty()
            }.sortedBy () { message ->
                message.timestamp
            }.fold(initial = mutableListOf<Message>()) {acc, message->
                    if (acc.isEmpty()) {
                        acc.add(message)
                    } else {
                        if (acc.last().isIncoming == message.isIncoming) {
                            val lastMessage = acc.removeLast()
                            val combinedMessage = Message(
                                id = lastMessage.id,
                                chatId = chatId,
                                // User
                                senderId = lastMessage.senderId,
                                text = lastMessage.text + " " + message.text,
                                mediaUri = null,
                                mediaMimeType = null,
                                timestamp = System.currentTimeMillis(),
                            )
                            acc.add(combinedMessage)
                        } else {
                            acc.add(message)
                        }
                    }
                    return@fold acc
                }


//            val collapsedHistory = pastMessages.fold(initial = mutableListOf<Content>()) {  ->
//                if (acc.isEmpty()) {
//                    acc.add(content)
//                } else {
//                    if (acc.last().role == content.role) {
//                        acc.last().parts.first()
//                    }
//                }
//                return@fold acc
//            }

            val lastUserMessage = pastMessages.removeLast()
            pastMessages.add(0, Message(
                id = 0L,
                chatId = chatId,
                // User
                senderId = 0L,
                text = "Please respond to this chat conversation like a friendly ${detail.firstContact.replyModel}.",
                mediaUri = null,
                mediaMimeType = null,
                timestamp = System.currentTimeMillis(),
            ))
            val pastContents = pastMessages.mapNotNull { message: Message ->
                val role = if (message.isIncoming) "model" else "user"
                return@mapNotNull content(role = role) { text(message.text) }
            }
            val chat = generativeModel.startChat(
                history = pastContents
            )


            var generateContentResult: GenerateContentResponse?
            try {
//                chat.history.add(content("user") { text(text) })
                generateContentResult = chat.sendMessage(lastUserMessage.text)
            } catch (e: Exception) {
                generateContentResult = null
            }
//        val safestResult = generateContentResult.candidates.minByOrNull { candidate: Candidate? ->
//            candidate?.safetyRatings.maxByOrNull { safetyRating : SafetyRating? ->
//                safetyRating?.probability ?: 1F
//            }
//        }
            val response = generateContentResult?.text ?: "GenAI failed :("

            messageDao.insert(
                Message(
                    id = 0L,
                    chatId = chatId,
                    // User
                    senderId = detail.firstContact.id,
                    text = response,
                    mediaUri = null,
                    mediaMimeType = null,
                    timestamp = System.currentTimeMillis(),
                ),
            )

            notificationHelper.pushShortcut(detail.firstContact, PushReason.IncomingMessage)
            // Show notification if the chat is not on the foreground.
            if (chatId != currentChat) {
                notificationHelper.showNotification(
                    detail.firstContact,
                    messageDao.loadAll(chatId),
                    false,
                )
            }
        }
    }

    suspend fun clearMessages() {
        messageDao.clearAll()
    }

    suspend fun updateNotification(chatId: Long) {
        val detail = chatDao.loadDetailById(chatId) ?: return
        val messages = messageDao.loadAll(chatId)
        notificationHelper.showNotification(
            detail.firstContact,
            messages,
            fromUser = false,
            update = true,
        )
    }

    fun activateChat(chatId: Long) {
        currentChat = chatId
        notificationHelper.dismissNotification(chatId)
    }

    fun deactivateChat(chatId: Long) {
        if (currentChat == chatId) {
            currentChat = 0
        }
    }

    suspend fun showAsBubble(chatId: Long) {
        val detail = chatDao.loadDetailById(chatId) ?: return
        val messages = messageDao.loadAll(chatId)
        notificationHelper.showNotification(detail.firstContact, messages, true)
    }

    suspend fun canBubble(chatId: Long): Boolean {
        val detail = chatDao.loadDetailById(chatId) ?: return false
        return notificationHelper.canBubble(detail.firstContact)
    }
}
