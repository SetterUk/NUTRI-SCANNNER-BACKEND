from langgraph.graph import StateGraph, END
from app.agents.state import AgentState
from app.agents.nodes import intent_node, ai_node, red_flag_node, synthesizer_node

workflow = StateGraph(AgentState)

workflow.add_node("intent", intent_node)
workflow.add_node("ai_analyst", ai_node)
workflow.add_node("red_flag", red_flag_node)
workflow.add_node("synthesizer", synthesizer_node)

def route_after_intent(state: AgentState):
    if state.get("is_food"):
        return ["ai_analyst", "red_flag"] 
    return ["synthesizer"]

workflow.set_entry_point("intent")

workflow.add_conditional_edges(
    "intent", 
    route_after_intent,
    ["ai_analyst", "red_flag", "synthesizer"] 
)

workflow.add_edge("ai_analyst", "synthesizer")
workflow.add_edge("red_flag", "synthesizer")

workflow.add_edge("synthesizer", END)
nutrition_app_workflow = workflow.compile()
