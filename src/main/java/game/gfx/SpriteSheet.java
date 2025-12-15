package main.java.game.gfx;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;


public class SpriteSheet {
    public final BufferedImage img;
    public final int tileW, tileH;


    public SpriteSheet(String resourcePath, int tileW, int tileH) {
        try {
            this.img = ImageIO.read(Objects.requireNonNull(
                    SpriteSheet.class.getResourceAsStream(resourcePath)
            ));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.tileW = tileW;
        this.tileH = tileH;
    }


    public BufferedImage sub(int idX, int idY) {
        return img.getSubimage(idX * tileW, idY * tileH, tileW, tileH);
    }
}
