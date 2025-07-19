#RULE TimeoutRule
#TIMEOUT 100ms
#ACTION
  Thread.sleep(50); // Succeeds
#FINAL
  return true;