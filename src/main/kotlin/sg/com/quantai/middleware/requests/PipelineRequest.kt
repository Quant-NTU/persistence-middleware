package sg.com.quantai.middleware.requests

import sg.com.quantai.middleware.data.NewStrategy

class PipelineRequest(
        val title: String,
        val description: String,
        val strategies_id: String,
)