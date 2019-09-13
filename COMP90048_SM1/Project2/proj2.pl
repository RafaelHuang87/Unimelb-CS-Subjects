:- ensure_loaded(library(clpfd)).

% Programming Name: COMP90048_SM1_Project2
% Author Name: Junjie Huang
% E-mail: junjhuang@student.unimelb.edu.au
% Programming introduction: It is a Maths Puzzles which can get a list of list
%                           with blanks as input and get a unique solution if 
%                           it exists. Some hints like hint 2 and some  
%                           functions like all_distinct, nth0 and maplist are 
%                           used to optimize the project. Firstly, the columns
%                           will be transposed to be rows. Secondly, check the
%                           diagonal by using check_diagonal function. Then,
%                           check the each row (original rows + rows which are
%                           transposed from columns). Finally, use maplist to 
%                           get the finall answer.
% Finish Time: 08/05/2019


% Function Name: check_diagonal
% Usage: Use recursion to determine if the ​diagonal values are the same, the
%        function nth0 is used in the first and third step with the same E to 
%        determine if the values ​​on two adjacent diagonals are the same.

check_diagonal([_|[]], _).
check_diagonal([First_Num, Second_Num| Tail], Index) :-
    nth0(Index, First_Num, Value, _),
    Indexs is Index + 1,
    nth0(Indexs, Second_Num, Value, _),
    check_diagonal([Second_Num | Tail], Indexs).


% Subfunction Name: sumlist
% Usage: Sum the list. If use the built-in function sum_list, an error will
%        occur because of the right side of the equation should be the ground
%        term when using "is". Thus, #= is being used for nonground terms  
%        instead of using "is" in this project.

sumlist([], 0).
sumlist([Head | Tail], Sum) :-
    sumlist(Tail, Temp),
    Sum #= Head + Temp.


% Subfunction Name: multipli_list
% Usage: Calculate the product of the list and  #= is being used for nonground 
%        terms with the same reason as sumlist.

productlist([], 1).
productlist([Head | Tail], Total_Pro) :-
    productlist(Tail, Pro),
    Total_Pro #= Head * Pro.


% Function Name: check_row
% Usage: Use recursion to determine the row by judging the digits from 1-9
%        by function ins with different numbers by function all_distinct,
%        and the sum or product is the head number.

check_row([]).
check_row([[X | Xs] | Tail]) :-
    Xs ins 1..9,
    all_distinct(Xs),
    (sumlist(Xs, X);
    productlist(Xs, X)),
    check_row(Tail).


% Function Name: puzzle_solution
% Usage: 
% 1. Transpose the column to the row
% 2. Set the three conditions
% 3. Use maplist(label, Puzzle) to get the 
%    ground terms

puzzle_solution([Head_Puzzle | Tail_Puzzle]) :-
    transpose([Head_Puzzle | Tail_Puzzle], [_ | ColTail_Puzzle]),
    check_diagonal(Tail_Puzzle, 1), check_row(Tail_Puzzle), check_row(ColTail_Puzzle),
    maplist(label, [Head_Puzzle | Tail_Puzzle]).

