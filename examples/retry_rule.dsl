#RULE RetryRule
#RETRY 3 DELAY 100 IF { error != null }
#PRODUCE
  result as Integer
#ACTION
  int count = context.get("count") != null ? (int)context.get("count") : 0;
  context.put("count", count + 1);
  if (count < 2) throw new Exception("Retry");
  result = 42;
#FINAL
  return result;