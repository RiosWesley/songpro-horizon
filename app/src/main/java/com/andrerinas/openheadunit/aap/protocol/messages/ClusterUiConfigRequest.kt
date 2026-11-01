package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.Message

/**
 * UI config for the cluster video channel: margins (the stream's unused border, as announced in
 * ServiceDiscovery) and content insets, the edges of the picture that the cluster theme covers,
 * where the phone keeps its cards and buttons out while the map itself runs on underneath.
 * Each array is left, top, right, bottom.
 */
class ClusterUiConfigRequest(margins: IntArray, contentInsets: IntArray) : AapMessage(
    Channel.ID_VID_CLUSTER,
    Media.MsgType.MEDIA_MESSAGE_UPDATE_UI_CONFIG_REQUEST_VALUE,
    makeProto(margins, contentInsets)
) {
    companion object {
        private fun insets(v: IntArray) = Media.Insets.newBuilder()
            .setLeft(v[0]).setTop(v[1]).setRight(v[2]).setBottom(v[3]).build()

        private fun makeProto(margins: IntArray, contentInsets: IntArray): Message {
            val uiConfig = Media.UiConfig.newBuilder()
                .setMargins(insets(margins))
                .setContentInsets(insets(contentInsets))
                .setStableContentInsets(insets(contentInsets))
                .build()
            return Media.UpdateUiConfigRequest.newBuilder().setUiConfig(uiConfig).build()
        }
    }
}
