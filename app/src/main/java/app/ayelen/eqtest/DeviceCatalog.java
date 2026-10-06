package app.ayelen.eqtest;
import java.util.Locale;

final class DeviceCatalog {
    static final String[] KEYS={"charge2","charge3","charge4","charge5","charge6","party110","party300","party310","boombox3","xp700"};
    static final String[] NAMES={"JBL Charge 2","JBL Charge 3","JBL Charge 4","JBL Charge 5","JBL Charge 6","JBL PartyBox 110","JBL PartyBox 300","JBL PartyBox 310","JBL Boombox 3","Sony XP700"};
    static String match(String name){
        if(name==null)return "generic";
        String s=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        for(int i=2;i<=6;i++)if(s.matches(".*charge"+i+"(?![0-9]).*"))return "charge"+i;
        for(int i:new int[]{110,300,310})if(s.matches(".*partybox"+i+"(?![0-9]).*"))return "party"+i;
        if(s.matches(".*boombox3(?![0-9]).*"))return "boombox3";
        if(s.matches(".*xp700(?![0-9]).*"))return "xp700";
        return "generic";
    }
}
