package com.sk89q.worldguard.neoforge.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Translate original WorldGuard options without changing the command's permission source. */
public final class LegacyCommandSyntax {
    private LegacyCommandSyntax() { }
    public static String translate(String input, UnaryOperator<String> resolveWorld) {
        String first=input.stripLeading().split("\\s+",2)[0].replaceFirst("^/", "").toLowerCase(Locale.ROOT);
        if(first.startsWith("worldguard:")) first=first.substring("worldguard:".length());
        if(!Set.of("rg","region","regions","stopfire","allowfire","god","ungod","heal","slay","wglocate","halt-activity","haltactivity","stoplag").contains(first)) return input;
        List<CommandWords.Word> words=new ArrayList<>(CommandWords.parse(input)); if(words.isEmpty()) return input;
        String root=words.getFirst().text().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
        if(root.startsWith("worldguard:")) root=root.substring("worldguard:".length());
        if(Set.of("halt-activity","haltactivity","stoplag").contains(root)) {
            boolean silent=false,resume=false,info=false,confirm=false;
            for(int index=1;index<words.size();index++) {
                String word=words.get(index).text();
                if(word.matches("-[cis]+")) { silent|=word.contains("s"); resume|=word.contains("c"); info|=word.contains("i"); }
                else if(word.equals("confirm")) confirm=true; else if(word.equals("resume")) resume=true;
                else throw new IllegalArgumentException("Use confirm, resume or -c/-i/-s with " + root);
            }
            return root + (silent ? " -s" : "") + (info ? " -i" : resume ? " resume" : confirm ? " confirm" : "");
        }
        if(Set.of("god","ungod","heal","slay","wglocate").contains(root)) {
            boolean silent=false; List<String> targets=new ArrayList<>();
            for(int index=1;index<words.size();index++) {
                var word=words.get(index);
                if(!word.quoted() && word.text().equals("-s")) silent=true;
                else targets.add(CommandWords.quote(word.text().equals("*") ? "@a" : word.text()));
            }
            return root + (silent ? " -s" : "") + (targets.isEmpty() ? "" : " " + String.join(" ",targets));
        }
        if(Set.of("stopfire","allowfire").contains(root) && words.size()==2) return root+" "+CommandWords.quote(resolveWorld.apply(words.get(1).text()));
        if(!Set.of("rg","region","regions").contains(root) || words.size()<2) return input;
        String world=null;
        for(int index=1;index<words.size();index++) if(!words.get(index).quoted() && words.get(index).text().equals("-w")) {
            if(world!=null || index+1>=words.size()) throw new IllegalArgumentException("Use one -w <world> option");
            world=resolveWorld.apply(words.get(index+1).text()); words.remove(index+1); words.remove(index--);
        }
        if(words.size()<2) throw new IllegalArgumentException("Specify a region command");
        String command=words.get(1).text().toLowerCase(Locale.ROOT);
        List<String> arguments=new ArrayList<>();
        boolean global=false,removeChildren=false,detachChildren=false,select=false,center=false,spawn=false;
        boolean nameOnly=false,allMembers=false,filteredList=false,changed=!root.equals(words.getFirst().text()) || !command.equals(words.get(1).text()) || world!=null;
        for(int index=2;index<words.size();index++) {
            var word=words.get(index); String text=word.text();
            if(!word.quoted()) {
                if(text.equals("-y") && Set.of("migratedb","migrateheights").contains(command)) { changed=true; continue; }
                if(text.equals("-n") && (Set.of("define","def","d","create","addowner","ao","addmember","addmem","am","removeowner","remowner","ro","removemember","remmember","removemem","remmem","rm").contains(command))) { nameOnly=true; changed=true; continue; }
                if(text.equals("-a") && Set.of("removeowner","remowner","ro","removemember","remmember","removemem","remmem","rm").contains(command)) { allMembers=true; changed=true; continue; }
                if(text.equals("-g") && Set.of("define","def","d","create","redefine","update","move").contains(command)) { global=true; changed=true; continue; }
                if(text.equals("-f") && Set.of("remove","delete","del","rem").contains(command)) { removeChildren=true; changed=true; continue; }
                if(text.equals("-u") && Set.of("remove","delete","del","rem").contains(command)) { detachChildren=true; changed=true; continue; }
                if((text.equals("-s") || text.equals("-u")) && Set.of("info","i").contains(command)) { select|=text.equals("-s"); changed=true; continue; }
                if(text.equals("-c") && Set.of("teleport","tp").contains(command)) { center=true; changed=true; continue; }
                if(text.equals("-s") && Set.of("teleport","tp").contains(command)) { spawn=true; changed=true; continue; }
                if(command.equals("list") && Set.of("-p","-i","-s","-n").contains(text)) filteredList=true;
            }
            arguments.add(CommandWords.quote(text));
        }
        if(removeChildren && detachChildren) throw new IllegalArgumentException("-f and -u cannot be combined");
        if(center && spawn) throw new IllegalArgumentException("-c and -s cannot be combined");
        if(global) command=Set.of("redefine","update","move").contains(command) ? "redefine-global" : "define-global";
        if(select) command="select";
        if(filteredList) { command="list-filtered"; changed=true; }
        if(command.equals("flags") && words.stream().anyMatch(word -> !word.quoted() && word.text().equals("-p"))) { command="flags-page"; changed=true; }
        if(removeChildren) arguments.add("children"); else if(detachChildren) arguments.add("force");
        if(nameOnly) arguments.add("-n"); if(allMembers) arguments.add("-a");
        if(center) arguments.add("center"); else if(spawn) arguments.add("spawn");
        if(Set.of("load","reload","save","write","migrateheights").contains(command) && arguments.size()==1 && world==null) {
            world=resolveWorld.apply(words.getLast().text()); arguments.clear(); changed=true;
        }
        if(!changed && !global) return input;
        return "rg " + (world==null ? "" : "in "+CommandWords.quote(world)+" ") + command + (arguments.isEmpty() ? "" : " "+String.join(" ",arguments));
    }
}
