fun <T> getT(): T = null!!

val foo = getT<List<String, List<Int>>>()
/*
psi: val foo = getT<List<String, List<Int>>>()
type: [Error type: Not found recorder type for getT<List<String, List<Int>>>()]
*/