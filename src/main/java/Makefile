# Showing classpath including current directory (.) and 3 jar files
# ":" is the separator between the items included in classpath
CLASSPATH = .
JFLAGS = -g -classpath $(CLASSPATH)
JC = javac
.SUFFIXES: .java .class

.java.class:
		$(JC) $(JFLAGS) $*.java

# Include all your .java  file names here
CLASSES = \
		Crawler.java

default: classes

classes:
		$(CLASSES=.class)

clean:
		$(RM) *.class