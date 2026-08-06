import time
import logging
import random
from typing import List, Dict, Optional
from groq import AsyncGroq
from app.core.config import settings

logger = logging.getLogger(__name__)

class GroqKeyManager:
    """
    Manages multiple Groq API keys, implementing load balancing and rate limit avoidance.
    """
    def __init__(self, api_keys: List[str]):
        if not api_keys:
            raise ValueError("No Groq API keys provided!")
        
        self.keys = []
        for key in api_keys:
            self.keys.append({
                "api_key": key,
                "client": AsyncGroq(api_key=key),
                "reset_time": 0.0,
                "is_rate_limited": False
            })
        logger.info(f"[GroqManager] Initialized with {len(self.keys)} API keys.")

    def get_best_client(self) -> AsyncGroq:
        """
        Returns an AsyncGroq client that is not currently rate-limited.
        If all are rate-limited, it returns the one that will reset soonest.
        """
        now = time.time()
        
        # Check if any rate-limited keys have passed their reset time
        for k in self.keys:
            if k["is_rate_limited"] and now >= k["reset_time"]:
                k["is_rate_limited"] = False
                logger.info(f"[GroqManager] Key {k['api_key'][:10]}... cooldown finished. Restored.")

        # Get available keys
        available_keys = [k for k in self.keys if not k["is_rate_limited"]]
        
        if available_keys:
            # Randomly load balance across available keys to avoid hitting limits quickly
            selected = random.choice(available_keys)
            return selected["client"]
        
        # ALL keys are rate-limited! Oh no.
        # Pick the one that will reset the soonest to minimize failure
        logger.warning("[GroqManager] ALL API keys are rate limited! Returning the soonest to reset.")
        soonest = min(self.keys, key=lambda k: k["reset_time"])
        return soonest["client"]

    def mark_rate_limited(self, client: AsyncGroq, retry_after_seconds: float = 60.0):
        """
        Marks a specific client's API key as rate limited.
        """
        for k in self.keys:
            if k["client"] == client:
                k["is_rate_limited"] = True
                k["reset_time"] = time.time() + retry_after_seconds
                logger.warning(f"[GroqManager] Key {k['api_key'][:10]}... marked as rate limited for {retry_after_seconds}s.")
                break

# Singleton instance
groq_manager = GroqKeyManager(settings.groq_api_keys_list)
