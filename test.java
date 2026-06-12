public class test {
    public static void main(String[] args) {
        String[] templates = {
            "&7[&aPara &f{bigbangessentials_displayname}&7] &f{MESSAGE}",
            "Target's prefix: {prefix} {0}",
            "{0,number,#.##} coins",
            "{1st_place} won",
            "{0} and {1}"
        };
        
        for (String t : templates) {
            String safe = t.replace("'", "''");
            safe = safe.replaceAll("\\{(?!\\d+\\}|\\d+,)([^}]+)\\}", "'{'$1'}'");
            System.out.println("Original: " + t);
            System.out.println("Safe:     " + safe);
            System.out.println("Formatted:");
            try {
                System.out.println(java.text.MessageFormat.format(safe, "ARG0", "ARG1"));
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
            System.out.println();
        }
    }
}
