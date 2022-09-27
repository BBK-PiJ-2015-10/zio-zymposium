package implicitsnotes

object ImplicitParameters extends App {

  def explicitMethod(int: Int) =
    s"Here's the int you gave me: $int"

  def implicitMethod(implicit int: Int) =
    s"Here's the Int: $int I found"


  println(explicitMethod(4))

  implicit val test = 5

  print(implicitMethod)

  //https://www.youtube.com/watch?v=83rm2LxdkAQ&list=PLvdARMfvom9C8ss18he1P5vOcogawm5uC&index=37
 //left on minuate 6:44


}
