import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogV2;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
public class scratch {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("common/src/main/resources/default-config/bigbangessentials/adminshop.yml"));
        Yaml yaml = new Yaml();
        try {
            AdminShopCatalogV2 catalog = yaml.loadAs(content, AdminShopCatalogV2.class);
            System.out.println("Stores: " + catalog.stores.size());
            System.out.println("Products: " + catalog.products.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
