%%

%unicode 6.1
%public
%class UnicodePropList_Noncharacter_Code_Point_6_1

%type int
%standalone

%include src/test/resources/common-unicode-binary-property-java

%%

\p{Noncharacter_Code_Point} { setCurCharPropertyValue(); }
[^] { }

<<EOF>> { printOutput(); return 1; }
