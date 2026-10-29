import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: PrayerStore
    @EnvironmentObject private var l10n: L10n

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
                Text(l10n.t("brand").uppercased())
                    .font(AppFont.display(19, .semibold))
                    .tracking(5)
                    .foregroundStyle(Azanify.Palette.gold)
                Text(l10n.date(store.now, in: store.city.timeZone))
                    .font(AppFont.text(12))
                    .foregroundStyle(Azanify.Palette.mistFaint)
            }

            Spacer()

            Menu {
                ForEach(PrayerStore.cities) { city in
                    Button {
                        store.select(city)
                    } label: {
                        if city.id == store.city.id {
                            Label(l10n.language == .az ? city.nameAz : city.nameEn, systemImage: "checkmark")
                        } else {
                            Text(l10n.language == .az ? city.nameAz : city.nameEn)
                        }
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 11))
                    Text(l10n.language == .az ? store.city.nameAz : store.city.nameEn)
                        .font(AppFont.text(13, .medium))
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
            SectionLabel(text: l10n.t("prayer.next"))

            HStack(alignment: .lastTextBaseline) {
                Text(nextTitle)
                    .font(AppFont.display(34, .semibold))
                    .foregroundStyle(Azanify.Palette.mist)
                Spacer()
                Text(store.nextSlot?.time ?? store.slots.first?.time ?? "--:--")
                    .font(AppFont.display(40, .regular))
                    .monospacedDigit()
                    .foregroundStyle(Azanify.Palette.goldSoft)
            }

            HStack(spacing: 7) {
                Image(systemName: "hourglass")
                    .font(.system(size: 11, weight: .semibold))
                Text(remainingText)
                    .font(AppFont.text(13))
            }
            .foregroundStyle(Azanify.Palette.mistDim)
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .azanifyCard()
    }

    private var prayerList: some View {
        VStack(spacing: 0) {
            ForEach(Array(store.slots.enumerated()), id: \.element.id) { index, slot in
                let isNext = slot.id == store.nextSlot?.id
                HStack(spacing: 12) {
                    Circle()
                        .fill(isNext ? Azanify.Palette.gold : Color.white.opacity(0.14))
                        .frame(width: 6, height: 6)

                    Text(l10n.t("prayer.names.\(slot.id)"))
                        .font(AppFont.text(15, isNext ? .semibold : .regular))
                        .foregroundStyle(isNext ? Azanify.Palette.goldSoft : Azanify.Palette.mist)
                        .opacity(slot.isObligatory ? 1 : 0.55)

                    Spacer()

                    Text(slot.time)
                        .font(AppFont.display(17, isNext ? .semibold : .regular))
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
        .azanifyCard()
    }

    private var footerNote: some View {
        Text(l10n.t("prayer.footer"))
            .font(AppFont.text(11))
            .foregroundStyle(Azanify.Palette.mistFaint)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 2)
    }

    private var nextTitle: String {
        l10n.t("prayer.names.\(store.nextSlot?.id ?? "fajr")")
    }

    private var remainingText: String {
        let total = store.remainingMinutes
        let h = total / 60
        let m = total % 60
        return h > 0
            ? String(format: l10n.t("prayer.remaining.h"), h, m)
            : String(format: l10n.t("prayer.remaining.m"), m)
    }
}
