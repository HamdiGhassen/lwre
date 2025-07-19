#RULE ComputeSum
#USE
  input1 : Integer as a FROM Global
  input2 : Integer as b FROM Global
#PRODUCE
  result as Integer
#ACTION
  result = a + b;
#FINAL
  return result;