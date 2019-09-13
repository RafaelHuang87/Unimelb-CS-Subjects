/*
Code Name: COMP90048_SM1_Assignment2
Finish Date: 29/04/2019
Author: Junjie Huang
E-mail: junjhuang@student.unimelb.edu.au
Introduction: This is a program using Prolog to achieve three different functions.
*/

/*
Function Name: correspond
Introduction: This holds when in one place where list L1 has the value E1, 
             L2 has E2. This must work in any mode in which L1 
             and L2 are proper lists (that is, either [] or a list whose t
             ail is a proper list).
*/
correspond(E1,[E1 | _], E2, [E2 | _]).
correspond(E1, [_ | A1], E2, [_ | A2]) :-
    correspond(E1, A1, E2, A2).

/*
Function Name: interleave
Introduction: This holds when Ls is a list of lists, and L is a list of all 
              the elements of all the lists in Ls, interleaved. 
              That is, the rst element of L is the rst element of the rst 
              list in Ls, the second element of L is the rst element of 
              the second list in Ls, and so on for all the lists in Ls. 
              After this, the next element of L is the second element 
              of the rst list in Ls, and so on, until two elements have been 
              taken from all the lists in Ls. After this, come the third 
              elements of all the lists in Ls, and so on, until all the 
              elements of all the lists in Ls are included in L. 
              All the lists in Ls must be the same length.
*/
get_headlist([], Y,[],Y).
get_headlist([[X | Xs] | Xss], [X | Ys], [Xs | Xssrest], Yrest) :-
    get_headlist(Xss, Ys, Xssrest, Yrest).
interleave([], []).
interleave([[] | Xs], []) :-
    interleave(Xs, []).
interleave([X | Xs], [Y | Ys]) :-
    get_headlist([X | Xs], [Y | Ys], Xrest, Yrest),
    interleave(Xrest, Yrest).

/*
Function Name: partial_eval
Introduction: This holds when Expr is the arithmetic expression Expr0 with 
              atom Var replaced by number Val, and any wholly numeric 
              subexpressions fully evaluated to numbers. This function 
              achieves five arithmetic expressions(+,-,*,/,//)
*/
partial_eval((Ele1 + Ele2), Var, Val, Final) :-
    partial_eval(Ele1, Var, Val, Temp1),
    partial_eval(Ele2, Var, Val, Temp2),
    (number(Temp1), number(Temp2), Final is (Temp1 + Temp2);
    (\+ (number(Temp1), number(Temp2)), Final = (Temp1 + Temp2))).

partial_eval((Ele1 - Ele2), Var, Val, Final) :-
    partial_eval(Ele1, Var, Val, Temp1),
    partial_eval(Ele2, Var, Val, Temp2),
    (number(Temp1), number(Temp2), Final is (Temp1 - Temp2);
    (\+ (number(Temp1), number(Temp2)), Final = (Temp1 - Temp2))).

partial_eval((Ele1 * Ele2), Var, Val, Final) :-
    partial_eval(Ele1, Var, Val, Temp1),
    partial_eval(Ele2, Var, Val, Temp2),
    (number(Temp1), number(Temp2), Final is (Temp1 * Temp2);
    (\+ (number(Temp1), number(Temp2)), Final = (Temp1 * Temp2))).

partial_eval((Ele1 / Ele2), Var, Val, Final) :-
    partial_eval(Ele1, Var, Val, Temp1),
    partial_eval(Ele2, Var, Val, Temp2),
    (number(Temp1), number(Temp2), Final is (Temp1 / Temp2);
    (\+ (number(Temp1), number(Temp2)), Final = (Temp1 / Temp2))).

partial_eval((Ele1 // Ele2), Var, Val, Final) :-
    partial_eval(Ele1, Var, Val, Temp1),
    partial_eval(Ele2, Var, Val, Temp2),
    (number(Temp1), number(Temp2), Final is (Temp1 // Temp2);
    (\+ (number(Temp1), number(Temp2)), Final = (Temp1 // Temp2))).

partial_eval(Expr0, Var, Val, Expr) :-
    atom(Var),
    number(Val),
    ((Expr0 = Var,Val = Expr); (Expr0 \= Var,Expr0 = Expr, (atom(Expr0); number(Expr0)))).
