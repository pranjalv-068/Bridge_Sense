from geniex import AutoModelForCausalLM
import json

SYSTEM_PROMPT = """
You are BridgeSense, an engineering assistant.

Rules:
- Use ONLY the provided facts.
- Never invent values.
- Never change severity.
- Never change forecast.
- Write 2-3 concise sentences.
"""

def build_prompt(severity, forecast, telemetry):

    facts = {
        "node": telemetry["node_id"],
        "severity": severity["level"],
        "severity_score": severity["score"],
        "health_index": telemetry["health_index"],
        "predicted_health": forecast["predicted_health"],
        "eta_days": forecast["eta_days"],
        "trend": forecast["trend"],
        "temperature": telemetry["temperature"],
        "frequency": telemetry["frequency"]
    }

    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": json.dumps(facts, indent=2)}
    ]
MODEL_ID = "ai-hub-models/Qwen3-4B"

class LLMService:

    def __init__(self):

        print("Loading Qwen3-4B...")

        self.model = AutoModelForCausalLM.from_pretrained(MODEL_ID)

        print("Ready.")

    def generate(self, severity, forecast, telemetry):

        messages = build_prompt(
            severity,
            forecast,
            telemetry
        )

        prompt = self.model.tokenizer.apply_chat_template(
            messages,
            add_generation_prompt=True
        )

        output = []

        for chunk in self.model.generate(
            prompt,
            max_new_tokens=180,
            stream=True
        ):
            output.append(chunk)

        return "".join(output).strip()

    def close(self):
        self.model.close()


llm = LLMService()