/**
 * Write your bot here!
 *
 * Also see `Models.kt` for additional information.
 */

package `in`.tagme.hitown.bot

import `in`.tagme.hitown.bot.plugins.BotAction
import `in`.tagme.hitown.bot.plugins.BotConfigValue
import `in`.tagme.hitown.bot.plugins.BotDetails
import `in`.tagme.hitown.bot.plugins.InstallBotBody
import `in`.tagme.hitown.bot.plugins.MessageBotBody
import `in`.tagme.hitown.bot.plugins.MessageBotResponse
import `in`.tagme.hitown.bot.plugins.MessageData
import `in`.tagme.hitown.bot.plugins.TagMeInResponse
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

/**
 * Bot instance.
 */
val bot = Bot()

const val ONE_HOUR_MS = 60 * 60 * 1000

fun niceNumber(value: Double): String {
    if (value > 1e12) {
        return "%.1fT".format(value / 1e12)
    }
    if (value > 1e9) {
        return "%.1fB".format(value / 1e9)
    }
    if (value > 1e6) {
        return "%.1fM".format(value / 1e6)
    }
    if (value > 1e3) {
        return "%.1fk".format(value / 1e3)
    }
    return Math.round(value).toString()
}


class Bot {
    /**
     * Bot details as shown in Hi Town.
     */
    val details = BotDetails(
        /**
         * Bot name.
         */
        name = "Tag Me In",
        /**
         * Bot description.
         */
        description = "Fetches and displays top content from Tag Me In (https://tagme.in) channels.",
        /**
         * Keywords that will cause a Hi Town group message to be sent to the bot.
         */
        keywords = listOf("!tmi"),
        /**
         * Available configuration options for the bot (optional).
         */
        config = emptyList()
    )

    /**
     * Validate the bot secret (optional).
     */
    suspend fun validateInstall(
        secret: String?
    ): Boolean = true

    /**
     * Handle the bot being installed in a Hi Town group.
     */
    suspend fun install(
        token: String,
        body: InstallBotBody
    ) {
        // Installation logic if needed
    }

    /**
     * Handle the bot being reinstalled in a Hi Town group due to config changes made by the group host.
     */
    suspend fun reinstall(
        token: String,
        config: List<BotConfigValue>
    ) {
        // Reinstallation logic if needed
    }

    /**
     * Handle the bot being uninstalled from a Hi Town group.
     */
    suspend fun uninstall(
        token: String
    ) {
        // Uninstallation logic if needed
    }

    /**
     * Handle the bot message.
     */
    fun message(
        token: String,
        body: MessageBotBody
    ): MessageBotResponse {
        val messageText = body.message?.trim() ?: "" // Trim and handle null message
        if (messageText.startsWith("!tmi", ignoreCase = true)) {
            val command = messageText.substringAfter("!tmi").trim() // Extract command after !tmi
            return try {
                val seekResponse = seekChannel(command) // Use extracted command as channel name
                if (seekResponse?.response?.messages != null) {
                    val topContentMarkdown = formatTopContent(command, seekResponse.response.messages)
                    MessageBotResponse(
                        success = true,
                        note = "Successfully fetched and formatted content for channel: $command",
                        actions = listOf(BotAction(message = topContentMarkdown))
                    )
                } else {
                    MessageBotResponse(
                        success = false,
                        note = "Failed to retrieve content from API for channel: $command or empty response",
                        actions = listOf(BotAction(message = "Sorry, I couldn't fetch content for channel '$command' right now. Channel might not exist or data is unavailable."))
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log the error for debugging
                MessageBotResponse(
                    success = false,
                    note = "Exception during content fetching for channel: $command: ${e.message}",
                    actions = listOf(BotAction(message = "Oops, something went wrong while fetching content for channel '$command'."))
                )
            }
        } else {
            return MessageBotResponse(
                success = true,
                note = "Not a TMI command",
                actions = emptyList() // No action for non-TMI commands
            )
        }
    }

    private fun seekChannel(channelName: String): TagMeInResponse? {
        val apiUrl = "https://tagme.in/seek?channel=${encodeURIComponent(channelName)}&hour=999999999999999"
        return try {
            val url = URI.create(apiUrl).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val gson = Gson()
                gson.fromJson(response.toString(), TagMeInResponse::class.java)
            } else {
                System.err.println("HTTP request failed with code: $responseCode for channel: $channelName")
                null // Or throw an exception
            }
        } catch (e: Exception) {
            e.printStackTrace() // Log the exception
            null // Or re-throw the exception
        }
    }

    // Helper function to properly encode channel name for URL (important for spaces and special characters)
    private fun encodeURIComponent(s: String): String {
        return java.net.URLEncoder.encode(s, "UTF-8")
    }

    private fun calculateScore(messageData: MessageData): Double {
        val now = System.currentTimeMillis()
        val position = messageData.position ?: 0.0 // Default to 0 if null
        val velocity = messageData.velocity ?: 0     // Default to 0 if null
        val timestamp = messageData.timestamp ?: 0L    // Default to 0 if null

        return position + (velocity * (now - timestamp)) / ONE_HOUR_MS
    }

    private fun formatTopContent(channelName: String, messages: Map<String, MessageData?>): String { // Added channelName to formatter
        if (messages.isEmpty()) {
            return "No content found for channel: $channelName."
        }

        val scoredContent = messages.entries.mapNotNull { (contentText, messageData) -> // Renamed variable for clarity
            messageData?.let {
                contentText to it
            }
        }.sortedByDescending { (_, messageData) ->
            calculateScore(messageData)
        }.take(10)


        val markdownBuilder = StringBuilder()
        val displayedChannelName = channelName.ifEmpty { "⌂" }
        val encodedChannel = URLEncoder.encode(channelName, "UTF-8")

        markdownBuilder.append("## Tag Me In Top 10 **[#${displayedChannelName}](https://tagme.in/#/$encodedChannel)**\n\n")

        if (scoredContent.isEmpty()) {
            return "No content found to display for channel: $channelName." // More specific message
        }

        scoredContent.forEach { (contentText, messageData) ->
            val score = calculateScore(messageData)
            contentText.split("\n").forEach { line ->
                markdownBuilder.append("> *\"${line.replace("\"", "\\\"")}\"*\n")
            }

            val encodedText = URLEncoder.encode(contentText, "UTF-8").replace("+", "%20")
            val base64EncodedText = Base64.getEncoder().encodeToString(encodedText.toByteArray())
            val link = "https://tagme.in/#/${encodedChannel}/${base64EncodedText}"
            val prettyScore = niceNumber(score)
            val scoreText = "score `$prettyScore` `(%.2f)`".format(score)
            markdownBuilder.append("[ᵀᴹᴵ]($link) $scoreText\n\n")
        }

        return markdownBuilder.toString()
    }
}