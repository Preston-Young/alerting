/*
 *   Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package org.opensearch.alerting.action

import org.opensearch.action.ActionResponse
import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.ToXContentObject
import org.opensearch.common.xcontent.XContentBuilder
import java.io.IOException

class ImportMonitorResponse : ActionResponse, ToXContentObject {
    var total: Int
    var successful: Int
    var failed: Int

    constructor(
        total: Int,
        successful: Int,
        failed: Int
    ) : super() {
        this.total = total
        this.successful = successful
        this.failed = failed
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readInt(), // total
        sin.readInt(), // successful
        sin.readInt() // failed
    )

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeInt(total)
        out.writeInt(successful)
        out.writeInt(failed)
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
            .field("total", total)
            .field("successful", successful)
            .field("failed", failed)
            .endObject()
    }
}
