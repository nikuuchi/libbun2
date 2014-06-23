#include <stdio.h>
#include <stdlib.h>

struct student {
	int number;
	char *name;
};

typedef struct student Student;

Student *new_Student(void) {
	Student *s = (Student *)malloc(sizeof(Student));
	s->number = 10;
	s->name = "naruto";
	return s;
}


int main(void) {
	Student *a = new_Student();
	printf("%d\n", a->number);
	printf("%s\n", a->name);
	return 0;
}
