
init:
	git submodule update --init --recursive

idea:
	mill -i mill.idea.GenIdea/idea

ManagerAndClientNode:
	mill -i chisel_diplomacy_test.test.runMain DiplomacyTest.ManagerAndClientNode.main -td build

EchoFields:
	mill -i chisel_diplomacy_test.test.runMain DiplomacyTest.EchoFields.main -td build

RequestFields:
	mill -i chisel_diplomacy_test.test.runMain DiplomacyTest.RequestFields.main -td build

