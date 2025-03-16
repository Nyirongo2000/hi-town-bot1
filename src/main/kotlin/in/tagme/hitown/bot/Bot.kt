/**
 * Write your bot here!
 *
 * Also see `Models.kt` for additional information.
 */

package `in`.tagme.hitown.bot

import com.google.gson.Gson
import `in`.tagme.hitown.bot.plugins.BotAction
import `in`.tagme.hitown.bot.plugins.BotConfigValue
import `in`.tagme.hitown.bot.plugins.BotDetails
import `in`.tagme.hitown.bot.plugins.InstallBotBody
import `in`.tagme.hitown.bot.plugins.LabelData
import `in`.tagme.hitown.bot.plugins.MessageBotBody
import `in`.tagme.hitown.bot.plugins.MessageBotResponse
import `in`.tagme.hitown.bot.plugins.MessageData
import `in`.tagme.hitown.bot.plugins.TagMeInResponse
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
        token: String, body: InstallBotBody
    ) {
        // Installation logic if needed
    }

    /**
     * Handle the bot being reinstalled in a Hi Town group due to config changes made by the group host.
     */
    suspend fun reinstall(
        token: String, config: List<BotConfigValue>
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
        token: String, body: MessageBotBody
    ): MessageBotResponse {
        val messageText = body.message?.trim() ?: "" // Trim and handle null message

        if (messageText.startsWith("!tmi", ignoreCase = true)) {
            var remainingText = messageText.substringAfter("!tmi").trim()
            println("Remaining text is: $remainingText")
            val labels = mutableListOf<String>()
            while (true) {
                val lastBracketIndex = remainingText.lastIndexOf(']')
                if (lastBracketIndex == -1) {
                    break
                }
                val firstBracketIndex = remainingText.lastIndexOf('[', lastBracketIndex)
                if (firstBracketIndex == -1) {
                    break
                }
                val labelPart = remainingText.substring(firstBracketIndex + 1, lastBracketIndex)
                val labelParts = labelPart.split(":")
                if (labelParts.size == 2 && labelParts[0].isNotEmpty() && labelParts[1].isNotEmpty()) {
                    val label = "${labelParts[0]}:${labelParts[1]}"
                    labels.add(label)
                }
                remainingText = remainingText.substring(0, firstBracketIndex).trim()
            }

            val channelName = remainingText.trim()
            println("Channel name is $channelName")
            val finalLabels = labels.reversed()
            println("Final labels are $finalLabels")
            return try {

                val seekResponse = if (finalLabels.isEmpty()) {
                    if (channelName.isEmpty()) {
                        MessageBotResponse(success = false, note = "No channel name", actions = listOf(BotAction(message = "No channel name specified")))
                    }
                    seekChannel(channelName)
                } else {
                    seekChannel(channelName, finalLabels)
                }?.takeIf { it.response?.messages?.isNotEmpty() ?: false }

                println("Parsed Labels are $finalLabels")
                if (seekResponse?.response?.messages != null) {
                    val topContentMarkdown = formatTopContent(
                        channelName, seekResponse.response.messages,
                        finalLabels
                    )
                    MessageBotResponse(
                        success = true,
                        note = "Successfully fetched and formatted content for channel: $channelName",
                        actions = listOf(BotAction(message = topContentMarkdown))
                    )
                } else {
                    MessageBotResponse(
                        success = false,
                        note = "Failed to retrieve content from API for channel: '$channelName' or empty response",
                        actions = listOf(BotAction(message = "Sorry, I couldn't fetch content for channel '$channelName' right now. Channel might not exist or data is unavailable."))
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log the error for debugging
                MessageBotResponse(
                    success = false,
                    note = "Exception during content fetching for channel: '$channelName': ${e.message}",
                    actions = listOf(BotAction(message = "Oops, something went wrong while fetching content for channel '$channelName'."))
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

    private fun seekChannel(
        channelName: String, labels: List<String> = emptyList()
    ): TagMeInResponse? { // Modified to match the final labels
        val apiUrl =
            "https://tagme.in/seek?channel=${encodeURIComponent(channelName)}&hour=999999999999999"

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

                if (labels.isNotEmpty()) {
                    println("Labels provided for channel $channelName: ${labels.joinToString(", ")}")
                }

                val gson = Gson()
                val channelData = gson.fromJson(response.toString(), TagMeInResponse::class.java)

                val filteredChannelData = if (labels.isNotEmpty()) {
                    // Filter messages based on provided labels
                    val filteredMessages =
                        channelData.response?.messages?.filter { (messageContent, messageData) ->
                            // Extract the top label (status) from the message data
                            val scoredLabels =
                                (messageData?.labels ?: emptyMap()).mapValues { (_, labelData) ->
                                    calculateScore(
                                        LabelData(
                                            position = labelData.position,
                                            velocity = labelData.velocity,
                                            timestamp = labelData.timestamp,
                                            seen = labelData.seen,
                                        )
                                    )
                                }

                            val topLabelPair = calculateTopLabel(scoredLabels)
                            val topLabel = topLabelPair?.first


                            // Check if any of the provided labels match the extracted top label
                            labels.any { providedLabel ->
                                providedLabel.equals(
                                    topLabel, ignoreCase = true
                                ) // Case-insensitive comparison
                            }
                        }

                    TagMeInResponse(
                        TagMeInResponse.response(
                            channels = emptyMap<String, Double>(),
                            messages = filteredMessages ?: emptyMap()
                        )
                    )
                } else {
                    // No labels provided, return the original data
                    channelData
                }

                if ((filteredChannelData.response?.messages?.isEmpty() == true) && labels.isNotEmpty()) {
                    println("No messages found with the labels provided: ${labels.joinToString(", ")}")
                }
                filteredChannelData
            } else {
                System.err.println("HTTP request failed with code: $responseCode for channel: $channelName")
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace() // Log the exception
            null // Or re-throw the exception
        }
    }

    // Helper function to properly encode channel name for URL (important for spaces and special characters)
    private fun encodeURIComponent(s: String): String {
        return URLEncoder.encode(s, "UTF-8")
    }

    private fun calculateScore(labelData: LabelData): Double {
        val now = System.currentTimeMillis()
        val position = labelData.position ?: 0.0 // Default to 0 if null
        val velocity = labelData.velocity ?: 0     // Default to 0 if null
        val timestamp = labelData.timestamp ?: 0L    // Default to 0 if null

        return position + (velocity * (now - timestamp)) / ONE_HOUR_MS
    }

    private fun calculateScore(messageData: MessageData): Double {
        val now = System.currentTimeMillis()
        val position = messageData.position ?: 0.0 // Default to 0 if null
        val velocity = messageData.velocity ?: 0     // Default to 0 if null
        val timestamp = messageData.timestamp ?: 0L    // Default to 0 if null

        return position + (velocity * (now - timestamp)) / ONE_HOUR_MS
    }

    private fun calculateTopLabel(labelsToCompare: Map<String, Double>): Pair<String, Double>? {
        if (labelsToCompare.isEmpty() || !labelsToCompare.keys.any { it.startsWith("status:") }) {
            return null
        }

        val statusLabels = labelsToCompare.filterKeys { it.startsWith("status:") }
        if (statusLabels.isEmpty()) {
            println("No status: labels")
            return null
        }

        val sortedTopLabels = statusLabels.entries.map { (label, score) ->
            label to score
        }.sortedByDescending { it.second }

        return sortedTopLabels.firstOrNull()
    }

    private fun formatTopContent(
        channelName: String, messages: Map<String, MessageData>, finalLabels: List<String>
    ): String { // Added channelName to formatter
        if (messages.isEmpty()) {
            return "No content found for channel: $channelName."
        }

        val scoredContent =
            messages.entries.map { (contentText, messageData) -> // Renamed variable for clarity
                messageData.let {
                    contentText to it
                }
            }.sortedByDescending { (_, messageData) ->
                calculateScore(messageData)
            }.take(10)

        val markdownBuilder = StringBuilder()
        val displayedChannelName = channelName.ifEmpty { "⌂" }
        val encodedChannel = URLEncoder.encode(channelName, "UTF-8")

        markdownBuilder.append("## Tag Me In Top 10 **[#${displayedChannelName}](https://tagme.in/#/$encodedChannel)**")
        if (finalLabels.isNotEmpty()) {
            val labelsSection = finalLabels.joinToString(" ") { "`$it`" }
            markdownBuilder.append(" with labels $labelsSection")
        }
        markdownBuilder.append("\n\n")

        if (scoredContent.isEmpty()) {
            return "No content found to display for channel: $channelName." // More specific message
        }

        scoredContent.forEach { (contentText, messageData) ->
            val score = calculateScore(messageData)
            val topLabelForMessage: String? = calculateTopLabel(
                (messageData.labels ?: emptyMap()).mapValues { (_, labelData) ->
                    calculateScore(labelData)
                }
            )?.first?.substringAfter("status:")

            if (topLabelForMessage != null) {
                markdownBuilder.append("> `status:$topLabelForMessage`\n")
            }

            contentText.split("\n").forEach { line ->
                markdownBuilder.append("> *\"${line.replace("\"", "\\\"")}\"*")
                markdownBuilder.append("\n")
            }

            val encodedText =
                URLEncoder.encode(contentText, "UTF-8").replace("+", "%20")
            val base64EncodedText = Base64.getEncoder().encodeToString(encodedText.toByteArray())
            val link = "https://tagme.in/#/${encodedChannel}/${base64EncodedText}"
            val prettyScore = niceNumber(score)
            val scoreText = "score `$prettyScore` `(%.2f)`".format(score)
            markdownBuilder.append("[ᵀᴹᴵ]($link) $scoreText\n\n")
        }

        return markdownBuilder.toString()
    }
}