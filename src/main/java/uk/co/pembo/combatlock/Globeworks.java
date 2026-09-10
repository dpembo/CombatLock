package uk.co.pembo.combatlock;

public class Globeworks {
    
    private static final String RESET = "\u001B[0m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String RED = "\u001B[31m";
    private static final String BROWN = "\u001B[33m";












    public static String logo(String pluginName, String version) {
        
        String logo = "\n" +
        
        YELLOW + "|`-._/\\_.-`| " +
        GREEN + " _______ _____   " + BLUE + "_______ " + GREEN + "______ " + BLUE + "_______\n" +
        YELLOW + "|    ||    | " +
        GREEN + "|     __|     |_|" + BLUE + "       |" + GREEN + "   __ \\" + BLUE + "    ___|\n" +
        YELLOW + "|___o()o___| " +
        GREEN + "|    |  |       |" + BLUE + "   -   |" + GREEN + "   __ <" + BLUE + "    ___|\n" +
        YELLOW + "|__((<>))__| " +
        BROWN + "|_______|_______|" + BLUE + "_______|" + BROWN + "______/" + BLUE + "_______\n" +
        YELLOW + "\\   o\\/o   / " +
        RED + " ________ _______ ______ __  __ _______\n" +
        YELLOW + " \\   ||   /  " +
        RED + "|  |  |  |       |   __ \\  |/  |     __|\n" +
        YELLOW + "  \\  ||  /   " +
        RED + "|  |  |  |   -   |      <     <|__     |\n" +
        YELLOW + "   '.||.'    " +
        RED + "|________|_______|___|__|__|\\__|_______|\n" +
        YELLOW + "     ``      " +
        YELLOW + "\n" +
        YELLOW + pluginName + " v" + version + "\n" +
        RESET;
        return logo;
    }
}
