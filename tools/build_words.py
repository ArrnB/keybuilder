#!/usr/bin/env python3
"""
Builds app/src/main/assets/words/words.txt, the word list behind suggestions and autocorrect.

Run it again whenever you want a bigger or smaller list:   pip install wordfreq english-words
                                                             python3 tools/build_words.py

Each line of the result is:  word <TAB> score <TAB> language
  score     how common the word is, 0..800 (100 x the "zipf" scale; 700+ is "the", 300 is rare).
            0 means: a real word, never suggested (rude words; they are also never "corrected").
  language  e = English, h = Hinglish (Hindi written in English letters), b = both
            Only English-only words (e) are ever autocorrected, and only towards other English words.

English comes from the wordfreq project's list: every word down to "fairly rare", plus rarer
ones only if an old public-domain dictionary (Webster's Second, the "web2" list) has them too.
That second check matters: among rare words a frequency list is full of misspellings, and a
misspelling that is in the list would never be corrected. Hinglish has no standard spelling and no
ready-made list, so it comes from two places: a hand-written list of the most common words
in the spellings people actually type, and wordfreq's Hindi list turned into English letters
by the rules below (rougher, so those words get a lower score). The keyboard also learns
your own spellings as you type, which matters more for Hinglish than any list.
"""
import os
import re
from wordfreq import top_n_list, zipf_frequency
from english_words import get_english_words_set

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "words", "words.txt")
ENGLISH_MIN_ZIPF = 3.0        # everything at least this common goes in
ENGLISH_RARE_ZIPF = 2.2       # down to here only words the dictionary also has
HINDI_WORDS = 9000
HINDI_PENALTY = 1.3   # transliterated spellings are guesses, so rank them below real lists

# ---------------------------------------------------------------- Hinglish, hand-written
COMMON_HINGLISH = {
    6.4: """hai nahi kya ka ki ke ko se me mein to bhi aur ho tha kar hi ye wo na""",
    6.0: """hain nhi kyu kyun main mai mujhe mera meri mere tum tu aap hum kuch koi sab ek ab abhi
            kaise kab kahan kaun toh par pe liye raha rahi rahe thi the hoga hogi gaya gayi kiya
            karna karo haan han acha theek bahut ya phir fir jab tab baat kaam log bhai yaar""",
    5.5: """kyon kyunki kyuki isliye lekin magar wala wali wale woh yeh vo yah iska uska unka inka
            tera teri tere tumhara tumhari tumhare aapka aapki aapke hamara hamari hamare hume humko
            mujhko tumhe tumko usko isko unko kisi kisko har sabhi kitna kitne kitni itna utna jitna
            accha achha thik sahi galat bohot bhot thoda thodi zyada jyada kam jaldi der aaj kal parso
            subah shaam raat din baad pehle pahle kabhi hamesha saath sath bina andar bahar upar neeche
            yahan wahan idhar udhar kaha yaha waha aise waise jaise kaisa kaisi konsa kon
            karta karti karte karunga karungi karenge karega karegi karke kiye kari karu karun
            hua hui hue hota hoti hote honge hoon hu hun tha
            gaye jaa jao jana jaata jaati jaate jaunga jaungi jayega jayenge jaa
            aa aao aana aaya aayi aaye aata aati aate aaunga aayega
            de dena diya diye dedo dega dunga le lo lena liya liye lega lunga
            bol bolo bolna bola boli bolte sun suno sunna suna dekh dekho dekhna dekha dekhi dekhte
            bata batao batana bataya chal chalo chalte chalna chala ruk ruko rukna
            mil milo milna mila mili milte milenge samajh samjha samjhi samjho pata malum maloom
            khana khaya khao pina piya pani paani chai ghar dost behen didi bhaiya mummy papa
            pyaar pyar dil zindagi paisa paise kaam naam jagah waqt time
            zaroor zarur bilkul shayad sach jhooth matlab mat bas sirf bhi abhi tak
            namaste shukriya dhanyavaad dhanyawad maaf alvida kripya ji
            kr kro krna rha rhi rhe hn thk ok haa nai""",
    # Short forms and other spellings people type every day. Having them here mostly matters
    # for autocorrect: a word in this list is never "fixed" into an English one.
    5.2: """h hu hn hun ha k b ni nai pr fr tk sb jb tb kb kse kch bs yr yrr bro mtlb lkn glt shi
            bhut bht bohat bhot pta smjh smjha kha mje mjhe mujhe tmhe tumhe abi abhi kbhi kabhi tabhi jabhi
            krta krti krte krunga krungi krenge krega kregi krdo krdi krdiya krke krne krni krwa
            gya gyi gye aya ayi aye jata jati jate jaoge jaogi jayegi jaega aaja aajao ajao chlo chl chle
            hota hoti hote hona hoge hogya hogyi hogaya hogayi hojaye hojayega
            wla wle wli vala vali vale mene maine tune tumne usne unhone humne hmne apne apna apni apka apki apke
            aapka aapki aapke tmhara tmhari hmara hmari uski uske iski iske unki unke inki inke kiska kiski kiske
            sabka sbka sabko sbko kidhar kahin kahi yahin wahin yahi wahi vahi
            thek thik tik bada badi bade bda chota choti chote chhota achi ache acche achhi achhe bura buri
            naya nayi naye nya purana purani sach jhoot dosti yar behen bhen maa baap beta beti ghr nam bat
            rat sham aj phle phele pese rupay rupaye rupee khao khaye khayi piyo so soja sogya soya uth utho
            bolta bolti bolunga bolungi bolega suni sunta sunti dekhta dekhti dekhunga dekhenge dikha dikhao
            batata bataunga bataungi btao btana btaya bta btaunga chalta chalti chali chalenge ruka ruki
            milta milti milunga milungi samjh samajhna samajhta lagta lagti lagte laga lagi lage lag lgta lgra
            rehna rehta rehti rehte rahna rahta rehne honge hoge kaisi kese kesa kesi kitne
            teen char paanch panch chhe saat aath nau das bees sau hazar hajar lakh crore
            arre arey are oye abe haina hena nahin nahii mat bilkul bikul zarurat jarurat zaruri jaruri
            mushkil asaan aasan khush khushi dukh dard gussa naraz pareshan tension
            padhai padhna padh likhna likh school college office kaam kaaj chutti
            shaadi shadi ladka ladki ladke bacha bacche bachcha aadmi aurat log dono sabse
            mast badhiya sahi ekdum pakka shayad lagbhag waise vaise jaisa jaisi itni utni jitni
            leke deke aake jaake karke bolke sunke dekhke milke rehke hoke
            kyunki kyuki kyoki isiliye islie tabtak jabtak abtak phirse firse wapas vapas dobara
            chahiye chaiye chahie chahta chahti chahte sakta sakti sakte skta skti skte paya payi paye
            hogi hoga higa lena dena lunga dunga lungi dungi loge doge legi degi
            yaad yad bhool bhul intezaar intezar wait soch socha sochna sochta
            khatam khtm shuru suru band bnd chalu khol kholo rakh rakho rkh nikal nikalo daal dalo
            jaldi jldi abhi turant dheere aaram aram se seedha ulta sidha
            gaadi gadi rasta raasta sheher shehar gaon gao desh duniya
            khud apnaap akela akeli saare sare saari sari poora pura puri adha aadha""",
}

# Chat shorthand. Listed so autocorrect leaves it alone ("hbd" must not become "had").
CHAT = """lol omg idk ikr tbh ngl smh brb lmao rofl hbd hru wyd gn gm tc btw imo fyi asap thx ty np pls plz
          ur kk okk hmm hmmm tmrw bcoz coz cuz gud nyc wat abt msg luv bday tho thru nite tonite gonna wanna
          gotta lemme ya yep nope dm pic pics bf gf bff irl tbf nvm ofc rn atm af wtf omw ily ttyl gtg
          haha hahaha hehe lmk wbu hbu jk yup nah yeah yea ok okay""".split()

# ---------------------------------------------------------------- Hindi letters -> English letters
CONSONANTS = {
    "क": "k", "ख": "kh", "ग": "g", "घ": "gh", "ङ": "n", "च": "ch", "छ": "chh", "ज": "j", "झ": "jh", "ञ": "n",
    "ट": "t", "ठ": "th", "ड": "d", "ढ": "dh", "ण": "n", "त": "t", "थ": "th", "द": "d", "ध": "dh", "न": "n",
    "प": "p", "फ": "ph", "ब": "b", "भ": "bh", "म": "m", "य": "y", "र": "r", "ल": "l", "व": "v", "श": "sh",
    "ष": "sh", "स": "s", "ह": "h", "ळ": "l",
    "क़": "q", "ख़": "kh", "ग़": "g", "ज़": "z", "ड़": "d", "ढ़": "dh", "फ़": "f",
}
VOWELS = {"अ": "a", "आ": "a", "इ": "i", "ई": "i", "उ": "u", "ऊ": "u", "ए": "e", "ऐ": "ai", "ओ": "o", "औ": "au", "ऋ": "ri", "ऑ": "o", "ऍ": "e"}
SIGNS = {"ा": "a", "ि": "i", "ी": "i", "ु": "u", "ू": "u", "े": "e", "ै": "ai", "ो": "o", "ौ": "au", "ृ": "ri", "ॉ": "o", "ॅ": "e"}
NUKTA, HALANT, ANUSVARA, CHANDRA, VISARGA = "़", "्", "ं", "ँ", "ः"


def hinglish(word):
    """One Hindi word in the English-letter spellings people commonly type (best first), or [] if it has other characters."""
    first = spell(word)
    if not first:
        return []
    out = [first]

    def also(text):
        if text not in out and re.fullmatch(r"[a-z]{2,}", text):
            out.append(text)
    for base in list(out):
        if "ph" in base:
            also(base.replace("ph", "f"))          # phir / fir
    if "ड़" in word:
        for base in list(out):
            also(spell(word.replace("ड़", "र")) or base)   # ladka / larka
    for base in list(out):
        if base.endswith("v") or "v" in base:
            also(base.replace("v", "w"))            # vala / wala
    return out


def spell(word):
    parts = []   # [consonants, vowel, has_inherent_a]
    i = 0
    while i < len(word):
        ch = word[i]
        nxt = word[i + 1] if i + 1 < len(word) else ""
        if ch in CONSONANTS:
            key = ch + NUKTA if nxt == NUKTA else ch
            if nxt == NUKTA:
                i += 1
                nxt = word[i + 1] if i + 1 < len(word) else ""
            cons = CONSONANTS.get(key, CONSONANTS[ch])
            if nxt == HALANT:
                parts.append([cons, "", False]); i += 2; continue
            if nxt in SIGNS:
                parts.append([cons, SIGNS[nxt], False]); i += 2; continue
            parts.append([cons, "a", True]); i += 1; continue
        if ch in VOWELS:
            parts.append(["", VOWELS[ch], False]); i += 1; continue
        if ch in (ANUSVARA, CHANDRA):
            parts.append(["n", "", False]); i += 1; continue
        if ch == VISARGA:
            parts.append(["h", "", False]); i += 1; continue
        return None
    if not parts:
        return None
    # The "a" inside a consonant is silent at the end of a word (कर = kar) and usually in the
    # middle when vowels sit on both sides (करना = karna, not karana).
    if parts[-1][2] and len(parts) > 1:
        parts[-1][1] = ""; parts[-1][2] = False
    for k in range(len(parts) - 2, 0, -1):
        if parts[k][2] and parts[k + 1][1] and parts[k - 1][1] and parts[k + 1][0]:
            parts[k][1] = ""; parts[k][2] = False
    # A long "aa" is written double at the start of a word (aap, aaj) and in short words (kaam, baat).
    long_a = [k for k, ch in enumerate(word) if ch in ("आ", "ा")]
    if word[0] == "आ" and parts[0][1] == "a":
        parts[0][1] = "aa"
    elif len(parts) == 2 and parts[0][0] and parts[0][1] == "a" and not parts[1][1] and long_a:
        parts[0][1] = "aa"
    text = "".join(c + v for c, v, _ in parts)
    text = text.replace("chchh", "chh")           # अच्छा: achchha -> achha
    text = re.sub(r"n([pb])", r"m\1", text)      # n before p/b sounds like m
    text = re.sub(r"in$", "i", text)              # नहीं: nahin -> nahi
    text = re.sub(r"en$", "ein", text)            # में: men -> mein
    return text if re.fullmatch(r"[a-z]{2,}", text) else None


# ---------------------------------------------------------------- putting it together
# Rude words: known (so never "corrected" into something else) but never suggested or learned.
QUIET_EXACT = """dick dicks cock cocks pussy fag fags retard retards lund randi""".split()
QUIET_STARTS = """fuck shit bitch asshole bastard cunt slut whore nigg faggot porn motherf bullshit
                  madarchod bhenchod behenchod chutiy gandu bhosd""".split()

words = {}   # word -> [score, language]


def add(word, zipf, lang):
    score = int(round(zipf * 100))
    if word in words:
        old = words[word]
        words[word] = [max(old[0], score), old[1] if old[1] == lang else "b"]
    else:
        words[word] = [score, lang]


dictionary = {w.lower() for w in get_english_words_set(["web2"], alpha=True)}
english = 0
for w in top_n_list("en", 250000):
    z = zipf_frequency(w, "en")
    if z < ENGLISH_RARE_ZIPF:
        break
    if not re.fullmatch(r"[a-z]+('[a-z]+)?", w) or (len(w) == 1 and w not in ("a", "i")):
        continue
    if z >= ENGLISH_MIN_ZIPF or (len(w) >= 3 and w in dictionary):
        add(w, z, "e"); english += 1

curated = 0
for z, text in COMMON_HINGLISH.items():
    for w in text.split():
        add(w, z, "h"); curated += 1

from_hindi = 0
for w in top_n_list("hi", HINDI_WORDS):
    z = zipf_frequency(w, "hi") - HINDI_PENALTY
    for n, latin in enumerate(hinglish(w)):
        zz = z - 0.3 * n      # second-choice spellings rank a little lower
        if zz < 2.6:
            break
        if latin in words and words[latin][1] in ("h", "b"):
            continue   # already there (hand-written, or from a more common Hindi word)
        add(latin, zz, "h"); from_hindi += 1

for w in CHAT:
    if w in words:
        words[w][1] = "b"          # "both": never treated as a slip for an English word
    else:
        words[w] = [330, "b"]

for w in words:
    if w in QUIET_EXACT or any(w.startswith(start) for start in QUIET_STARTS):
        words[w][0] = 0

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    for w in sorted(words):
        f.write(f"{w}\t{words[w][0]}\t{words[w][1]}\n")

print(f"english {english}, hinglish hand-written {curated}, from the Hindi list {from_hindi}, total {len(words)}")
if __name__ == "__main__" and "--sample" in __import__("sys").argv:
    sample = ["के", "है", "में", "नहीं", "क्या", "करना", "लड़का", "समझ", "नमस्ते", "ज़िंदगी", "हिंदी", "अच्छा", "बहुत", "दोस्त", "प्यार", "किताब", "सरकार", "भारत", "लोग", "कहानी"]
    print([hinglish(w) for w in sample])
