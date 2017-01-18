rmdir /s /q tmpout
mkdir tmpout
javac annotation/*.java annotation/qual/*.java annotation/userindex/*.java annotation/userindex/qual/*.java -cp C:\Users\neil\.m2\repository\org\checkerframework\checker\2.1.7\checker-2.1.7.jar -d tmpout
cd tmpout
jar cvf valueann.jar annotation
copy valueann.jar ..\..\lib