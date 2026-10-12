package com.sk89q.worldguard.neoforge.command;

import java.util.ArrayList;
import java.util.List;

public final class CommandWords {
    public record Word(String text, boolean quoted) { }
    private CommandWords() { }
    public static List<Word> parse(String input) {
        List<Word> result=new ArrayList<>(); int index=0;
        while(index<input.length()) {
            while(index<input.length() && Character.isWhitespace(input.charAt(index))) index++;
            if(index==input.length()) break;
            char quote=input.charAt(index); boolean quoted=quote=='"' || quote=='\'';
            if(quoted) index++;
            StringBuilder word=new StringBuilder(); boolean closed=!quoted;
            while(index<input.length()) {
                char character=input.charAt(index++);
                if(quoted && character==quote) { closed=true; break; }
                if(!quoted && Character.isWhitespace(character)) break;
                if(quoted && character=='\\' && index<input.length() && (input.charAt(index)==quote || input.charAt(index)=='\\')) character=input.charAt(index++);
                word.append(character);
            }
            if(!closed) throw new IllegalArgumentException("Unclosed command quotation");
            result.add(new Word(word.toString(),quoted));
        }
        return result;
    }
    public static String quote(String value) {
        return value.isEmpty() || value.chars().anyMatch(c->Character.isWhitespace(c) || c=='"' || c=='\\')
                ? "\"" + value.replace("\\","\\\\").replace("\"","\\\"") + "\"" : value;
    }
}
