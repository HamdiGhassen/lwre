#RULE Rule1
#PRODUCE
  result1 as Integer
#ACTION
  result1 = 21;

#RULE Rule2
#USE
  result1 : Integer as input FROM RULE Rule1
#PRODUCE
  result2 as Integer
#ACTION
  result2 = input * 2;
#FINAL
  return result2;