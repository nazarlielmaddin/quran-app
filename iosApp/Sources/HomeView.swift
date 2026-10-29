import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: PrayerStore

    private let ticker = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            Azanify.background.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    heroCard
                    prayerList
                    footerNote
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 28)
            }
        }
        .onReceive(ticker) { _ in store.tick() }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 3) {
                Text("AZANIFY")
                    .font(.system(size: 19, weight: .semibold))
                    .tracking(5)
                    .foregroundStyle(Azanify.Palette.gold)
                Text(Loc.longDate(store.now))
                    .font(.system(size: 12))
                    .foregroundStyle(Azanify.Palette.mistFaint)
            }

            Spacer()

            Menu {
                ForEach(PrayerStore.cities) { city in
                    Button {
                        store.select(city)
                    } label: {
                        if city.id == store.city.id {
                            Label(city.nameAz, systemImage: "checkmark")
                        } else {
                            Text(city.nameAz)
                        }
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 11))
                    Text(store.city.nameAz)
                        .font(.system(size: 13, weight: .medium))
                }
                .foregroundStyle(Azanify.Palette.mist)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
            }
        }
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionLabel(text: "Növbəti namaz")

            HStack(alignment: .lastTextBaseline) {
                Text(store.nextTitle)
                    .font(.system(size: 34, weight: .semibold, design: .serif))
                    .foregroundStyle(Azanify.Palette.mist)
                Spacer()
                Text(store.nextTime)
                    .font(.system(size: 40, weight: .light, design: .serif))
                    .monospacedDigit()
                    .foregroundStyle(Azanify.Palette.goldSoft)
            }

            HStack(spacing: 7) {
                Image(systemName: "hourglass")
                    .font(.system(size: 11, weight: .semibold))
                Text(store.remainingText)
                    .font(.system(size: 13))
            }
            .foregroundStyle(Azanify.Palette.mistDim)
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Azanify.card)
    }

    private var prayerList: some View {
        VStack(spacing: 0) {
            ForEach(Array(store.slots.enumerated()), id: \.element.id) { index, slot in
                let isNext = slot.id == store.nextSlot?.id
                HStack(spacing: 12) {
                    Circle()
                        .fill(isNext ? Azanify.Palette.gold : Color.white.opacity(0.14))
                        .frame(width: 6, height: 6)

                    Text(Loc.prayer(slot.id))
                        .font(.system(size: 15, weight: isNext ? .semibold : .regular))
                        .foregroundStyle(isNext ? Azanify.Palette.goldSoft : Azanify.Palette.mist)
                        .opacity(slot.isObligatory ? 1 : 0.55)

                    Spacer()

                    Text(slot.time)
                        .font(.system(size: 17, weight: isNext ? .semibold : .regular, design: .serif))
                        .monospacedDigit()
                        .foregroundStyle(isNext ? Azanify.Palette.goldSoft : Azanify.Palette.mistDim)
                }
                .padding(.vertical, 13)

                if index != store.slots.count - 1 {
                    Rectangle()
                        .fill(Color.white.opacity(0.05))
                        .frame(height: 1)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 4)
        .background(Azanify.card)
    }

    private var footerNote: some View {
        Text("Vaxtlar cihazda, oflayn hesablanır — Fəcr 18°, İşa 17°, Əsr kölgə əmsalı 1 (Şafi).")
            .font(.system(size: 11))
            .foregroundStyle(Azanify.Palette.mistFaint)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 2)
    }
}
