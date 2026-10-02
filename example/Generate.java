import io.github.hanshs474.kavel.Kavel;
import io.github.hanshs474.kavel.KavelException;

/** java -cp kavel.jar example/Generate.java "an isometric coffee shop, pastel palette" */
public class Generate {
    public static void main(String[] args) throws Exception {
        String prompt = args.length > 0 ? String.join(" ", args) : "an isometric coffee shop, pastel palette";
        Kavel kavel = Kavel.create();
        System.out.println("free grant: " + kavel.credits());
        try {
            System.out.println(kavel.generate(prompt));
        } catch (KavelException e) {
            System.err.println(e.reason() + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
