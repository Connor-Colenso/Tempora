package com.myname.mymodid.Particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

public class RedBoxParticle extends EntityFX {

    protected RedBoxParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleRed = 1.0F; // Red color
        this.particleGreen = 0.0F;
        this.particleBlue = 0.0F;
        this.particleAlpha = 1.0F;
        this.particleScale = 0.9F; // Slightly smaller than a block
        this.setParticleTextureIndex(1); // You'll need a texture for this
    }

    // Override other necessary methods if needed

    @Override
    public void renderParticle(Tessellator tessellator, float partialTicks, float rotX, float rotZ, float rotYZ,
        float rotXY, float rotXZ) {
        float minU = 0.0F;
        float maxU = 1.0F;
        float minV = 0.0F;
        float maxV = 1.0F;
        float scale = 0.1F * this.particleScale; // Slightly smaller than a block
        float xPos = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpPosX);
        float yPos = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpPosY);
        float zPos = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpPosZ);

        tessellator.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha);
        tessellator.addVertexWithUV(
            xPos - rotX * scale - rotXY * scale,
            yPos - rotZ * scale,
            zPos - rotYZ * scale - rotXZ * scale,
            maxU,
            maxV);
        tessellator.addVertexWithUV(
            xPos - rotX * scale + rotXY * scale,
            yPos + rotZ * scale,
            zPos - rotYZ * scale + rotXZ * scale,
            maxU,
            minV);
        tessellator.addVertexWithUV(
            xPos + rotX * scale + rotXY * scale,
            yPos + rotZ * scale,
            zPos + rotYZ * scale + rotXZ * scale,
            minU,
            minV);
        tessellator.addVertexWithUV(
            xPos + rotX * scale - rotXY * scale,
            yPos - rotZ * scale,
            zPos + rotYZ * scale - rotXZ * scale,
            minU,
            maxV);
    }

}
