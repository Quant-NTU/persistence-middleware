package sg.com.quantai.middleware.requests

class PipelineRequest(
        val title: String,
        val description: String,
        val strategies_id: String,
        val portfolio_id: String,
        val execution_method: String,
)