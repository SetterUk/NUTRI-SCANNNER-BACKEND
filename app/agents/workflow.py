from langgraph.graph import StateGraph, END
from app.agents.state import AgentState
from app.agents.nodes import intent_agent, orchestrator_node, nutritionist_agent, response_synthesizer

workflow = StateGraph(AgentState)

workflow.add_node("intent_agent", intent_agent)
workflow.add_node("nutritionist_agent", nutritionist_agent)
workflow.add_node("response_synthesizer", response_synthesizer)

workflow.set_entry_point("intent_agent")

workflow.add_conditional_edges(
    "intent_agent", 
    orchestrator_node,
    {
        "nutritionist_agent": "nutritionist_agent",
        "response_synthesizer": "response_synthesizer"
    }
)

workflow.add_edge("nutritionist_agent", "response_synthesizer")
workflow.add_edge("response_synthesizer", END)

nutrition_app_workflow = workflow.compile()
