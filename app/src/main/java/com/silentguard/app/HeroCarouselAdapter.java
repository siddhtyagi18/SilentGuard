package com.silentguard.app;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HeroCarouselAdapter extends RecyclerView.Adapter<HeroCarouselAdapter.HeroViewHolder> {

    private List<HeroItem> items;

    public HeroCarouselAdapter(List<HeroItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public HeroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hero_carousel, parent, false);
        return new HeroViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HeroViewHolder holder, int position) {
        HeroItem item = items.get(position);
        holder.title1.setText(item.getTitle1());
        holder.title2.setText(item.getTitle2());
        holder.desc.setText(item.getDesc());
        holder.shieldIcon.setImageResource(item.getIconRes());
        
        // Update dots
        holder.dot1.setAlpha(position == 0 ? 1.0f : 0.2f);
        holder.dot2.setAlpha(position == 1 ? 1.0f : 0.2f);
        holder.dot3.setAlpha(position == 2 ? 1.0f : 0.2f);

        // Apply theme colors and animations
        holder.applyThemeAndAnimations(item.getAnimType());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeroViewHolder extends RecyclerView.ViewHolder {
        TextView title1, title2, desc;
        ImageView shieldIcon, orbitIcon1, orbitIcon2, orbitIcon3;
        View orbit1, orbit2, dot1, dot2, dot3, glowDot1, glowDot3, shieldContainer;
        View particlePlus1, particlePlus2, heroBase, ambientGlow, shieldBg, orbitingContainer;

        HeroViewHolder(@NonNull View itemView) {
            super(itemView);
            title1 = itemView.findViewById(R.id.hero_title_1);
            title2 = itemView.findViewById(R.id.hero_title_2);
            desc = itemView.findViewById(R.id.hero_desc);
            shieldIcon = itemView.findViewById(R.id.hero_shield_icon);
            orbit1 = itemView.findViewById(R.id.hero_orbit_1);
            orbit2 = itemView.findViewById(R.id.hero_orbit_2);
            dot1 = itemView.findViewById(R.id.dot_1);
            dot2 = itemView.findViewById(R.id.dot_2);
            dot3 = itemView.findViewById(R.id.dot_3);
            glowDot1 = itemView.findViewById(R.id.glow_dot_1);
            glowDot3 = itemView.findViewById(R.id.glow_dot_3);
            particlePlus1 = itemView.findViewById(R.id.particle_plus_1);
            particlePlus2 = itemView.findViewById(R.id.particle_plus_2);
            heroBase = itemView.findViewById(R.id.hero_base_platform);
            shieldContainer = itemView.findViewById(R.id.hero_shield_container);
            ambientGlow = itemView.findViewById(R.id.hero_ambient_glow);
            shieldBg = itemView.findViewById(R.id.hero_shield_bg);
            orbitingContainer = itemView.findViewById(R.id.orbiting_icons_container);
            orbitIcon1 = itemView.findViewById(R.id.orbit_icon_1);
            orbitIcon2 = itemView.findViewById(R.id.orbit_icon_2);
            orbitIcon3 = itemView.findViewById(R.id.orbit_icon_3);
        }

        void applyThemeAndAnimations(int type) {
            shieldContainer.clearAnimation();
            orbit1.clearAnimation();
            orbit2.clearAnimation();
            glowDot1.clearAnimation();
            glowDot3.clearAnimation();
            particlePlus1.clearAnimation();
            particlePlus2.clearAnimation();
            heroBase.clearAnimation();
            orbitingContainer.clearAnimation();
            ambientGlow.clearAnimation();
            orbitIcon1.clearAnimation();
            orbitIcon2.clearAnimation();
            orbitIcon3.clearAnimation();
            itemView.findViewById(R.id.hero_anim_container).clearAnimation();

            int themeColor;
            if (type == 0) { // Green Slide
                themeColor = Color.parseColor("#22C55E");
                title2.setTextColor(themeColor);
                orbitIcon1.setVisibility(View.GONE);
                orbitIcon2.setVisibility(View.GONE);
                orbitIcon3.setVisibility(View.GONE);
                glowDot1.setVisibility(View.VISIBLE);
                glowDot3.setVisibility(View.VISIBLE);
            } else if (type == 2) { // Purple Slide
                themeColor = Color.parseColor("#8B5CF6");
                title2.setTextColor(themeColor);
                orbitIcon1.setVisibility(View.GONE);
                orbitIcon2.setVisibility(View.GONE);
                orbitIcon3.setVisibility(View.GONE);
                glowDot1.setVisibility(View.VISIBLE);
                glowDot3.setVisibility(View.VISIBLE);
            } else { // SOS Slide
                themeColor = Color.parseColor("#F97316");
                title2.setTextColor(themeColor);
                orbitIcon1.setVisibility(View.VISIBLE);
                orbitIcon2.setVisibility(View.VISIBLE);
                orbitIcon3.setVisibility(View.VISIBLE);
                glowDot1.setVisibility(View.GONE);
                glowDot3.setVisibility(View.GONE);
            }

            ambientGlow.getBackground().setTint(themeColor);
            orbit1.getBackground().setTint(themeColor);
            orbit2.getBackground().setTint(themeColor);
            heroBase.getBackground().setTint(themeColor);
            shieldBg.getBackground().setTint(themeColor);
            
            // Ensure background elements are extremely subtle (Remove "dark" feel)
             // Increased visibility for Green slide (type 0) as requested
             if (type == 0) {
                 ambientGlow.setAlpha(0.15f);
                 orbit1.setAlpha(0.35f);
                 orbit2.setAlpha(0.3f);
                 heroBase.setAlpha(0.3f);
                 shieldBg.setAlpha(0.12f);
             } else {
                 ambientGlow.setAlpha(0.08f);
                 orbit1.setAlpha(0.2f);
                 orbit2.setAlpha(0.15f);
                 heroBase.setAlpha(0.15f);
                 shieldBg.setAlpha(0.05f);
             }

            // Pulse for central icon
            Animation pulse = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.pulse);
            shieldContainer.startAnimation(pulse);
            
            // Overall container gentle float (More pronounced)
            Animation floatAnim = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.float_fade);
            floatAnim.setDuration(3000);
            itemView.findViewById(R.id.hero_anim_container).startAnimation(floatAnim);

            // Ambient glow breathing
            Animation breathing = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.float_fade);
            breathing.setDuration(2500);
            ambientGlow.startAnimation(breathing);

            // Orbit Rotations (Faster for clarity)
            Animation rotateSlow = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.rotate_orbit);
            rotateSlow.setDuration(10000);
            Animation rotateFastReverse = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.rotate_orbit_reverse);
            rotateFastReverse.setDuration(6000);

            orbit1.startAnimation(rotateSlow);
            orbit2.startAnimation(rotateFastReverse);
            heroBase.startAnimation(rotateSlow);

            if (type == 1) { // SOS specific icons rotation
                Animation rotateIcons = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.rotate_orbit);
                rotateIcons.setDuration(8000);
                orbitingContainer.startAnimation(rotateIcons);
                
                // Counter-rotation for icons (Upright) + Inverse Perspective Correction
                // Since the container is rotatedX=72, we need to counter-rotate the icons to look flat
                Animation keepUpright = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.rotate_orbit_reverse);
                keepUpright.setDuration(8000);
                
                orbitIcon1.startAnimation(keepUpright);
                orbitIcon2.startAnimation(keepUpright);
                orbitIcon3.startAnimation(keepUpright);
            } else { // Drifting particles
                Animation float1 = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.float_fade);
                float1.setDuration(2800);
                glowDot1.startAnimation(float1);
                
                Animation float2 = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.float_fade);
                float2.setDuration(3500);
                glowDot3.startAnimation(float2);

                Animation float3 = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.float_fade);
                float3.setDuration(4200);
                particlePlus1.startAnimation(float3);

                Animation float4 = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.float_fade);
                float4.setDuration(3200);
                particlePlus2.startAnimation(float4);
            }
        }
    }

    public static class HeroItem {
        private String title1, title2, desc;
        private int iconRes, animType;

        public HeroItem(String title1, String title2, String desc, int iconRes, int animType) {
            this.title1 = title1;
            this.title2 = title2;
            this.desc = desc;
            this.iconRes = iconRes;
            this.animType = animType;
        }

        public String getTitle1() { return title1; }
        public String getTitle2() { return title2; }
        public String getDesc() { return desc; }
        public int getIconRes() { return iconRes; }
        public int getAnimType() { return animType; }
    }
}